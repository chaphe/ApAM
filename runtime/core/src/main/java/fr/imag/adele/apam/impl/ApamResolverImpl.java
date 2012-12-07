package fr.imag.adele.apam.impl;


import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.osgi.framework.Filter;

import fr.imag.adele.apam.ApamManagers;
import fr.imag.adele.apam.ApamResolver;
import fr.imag.adele.apam.CST;
import fr.imag.adele.apam.Component;
import fr.imag.adele.apam.Composite;
import fr.imag.adele.apam.CompositeType;
import fr.imag.adele.apam.DependencyManager;
import fr.imag.adele.apam.Implementation;
import fr.imag.adele.apam.Instance;
import fr.imag.adele.apam.ManagerModel;
import fr.imag.adele.apam.Specification;
import fr.imag.adele.apam.declarations.DependencyDeclaration;
import fr.imag.adele.apam.declarations.DependencyPromotion;
import fr.imag.adele.apam.declarations.ComponentDeclaration;
import fr.imag.adele.apam.declarations.ComponentReference;
import fr.imag.adele.apam.declarations.ImplementationDeclaration;
import fr.imag.adele.apam.declarations.ImplementationReference;
import fr.imag.adele.apam.declarations.InterfaceReference;
import fr.imag.adele.apam.declarations.MessageReference;
import fr.imag.adele.apam.declarations.ResolvableReference;
import fr.imag.adele.apam.declarations.SpecificationReference;
import fr.imag.adele.apam.util.ApamFilter;
import fr.imag.adele.apam.util.Select;
import fr.imag.adele.apam.util.Util;

public class ApamResolverImpl implements ApamResolver {
	private APAMImpl apam;
	static Logger logger = LoggerFactory.getLogger(ApamResolverImpl.class);

	public ApamResolverImpl(APAMImpl theApam) {
		this.apam = theApam;
	}

	/**
	 * Compares the client dependency wrt the enclosing composite dependencies.
	 * If it matches one composite dependency, it is a promotion.
	 * Must use the composite dependency, but WARNING it may not fit the client's requirements
	 * if client's constraints does not match the composite selection.
	 * The client becomes the embedding composite; visibility and scope become the one of the embedding composite
	 *
	 * @param client
	 * @param dependency definition
	 * @return the composite dependency from the composite.
	 */
	private  DependencyDeclaration getPromotion(Instance client, DependencyDeclaration dependency) {

		//		DependencyDeclaration promotion = null;
		if (client.getComposite().getDeclaration() == null)
			return null;

		// take the dependency first in the instance, and if not found, in the implementation
		Set<DependencyDeclaration> compoDeps = Util.computeAllDependencies(client.getComposite()) ;
		if (compoDeps.isEmpty()) return null ;

		//look if a promotion is explicitly declared for that client component
		// <promotion implementation="A" dependency="clientDep" to="compoDep" />
		// <promotion specification="SA" dependency="clientDep" to="compoDep" />
		for (DependencyPromotion promo: client.getComposite().getCompType().getCompoDeclaration().getPromotions()) {
			if (! promo.getContentDependency().getIdentifier().equals(dependency.getIdentifier()))
				continue;		// this promotion is not about our dependency  (not "clientDep")

			String sourceName = promo.getContentDependency().getDeclaringComponent().getName();
			//sourceName = "SA" or "A"
			if (sourceName.equals(client.getImpl().getName()) || sourceName.equals(client.getSpec().getName())) {
				// We found the right promotion from client side.
				//Look for the corresponding composite dependency "compoDep"
				String toName =  promo.getCompositeDependency().getIdentifier();
				for (DependencyDeclaration compoDep : compoDeps) {
					if (compoDep.getIdentifier().equals(toName)) {
						//We found the composite side. It is an explicit promotion. It should match.
						if (Util.matchDependency (client, compoDep, dependency))
							return compoDep ;
						logger.error ("Promotion is invalid. Dependency " + promo.getContentDependency().getIdentifier()
								+ " of component " + sourceName + " does not match the composite dependency " + compoDep) ;
						return null ;
					}
				}
			}
		}

		for (DependencyDeclaration compoDep : compoDeps) {
			if  (Util.matchDependency(client, compoDep, dependency))
				return compoDep ;
		}
		return null ;
	}

	// if the instance is unused, it will become the main instance of a new composite.
	private Composite getClientComposite(Instance mainInst) {

		if (mainInst.isUsed())
			return mainInst.getComposite();

		/*
		 * We are resolving a reference from an unused client instance. We automatically build a new composite
		 * to create a context of execution. This allow to use Apam without requiring the explicit definition of
		 * composites, just instantiating any implementation.
		 */

		Implementation mainComponent			= mainInst.getImpl();
		String applicationName 					= mainComponent.getName() + "_Appli";
		SpecificationReference specification	= mainComponent.getImplDeclaration().getSpecification();
		Set<ManagerModel> models				= new HashSet<ManagerModel>();

		CompositeType application = apam.createCompositeType(null,
				applicationName, specification != null ? specification.getName() : null, mainComponent.getName(),
						models, null);

		/*
		 * Create an instance of the application with the specified main
		 */
		Map<String, String> initialProperties = new HashMap<String, String>();
		initialProperties.put(CST.APAM_MAIN_INSTANCE, mainInst.getName()) ;
		return (Composite)application.createInstance(null, initialProperties);
	}

	/**
	 * An APAM client instance requires to be wired with one or all the instances that satisfy the dependency.
	 * WARNING : in case of interface or message dependency , since more than one specification can implement the same
	 * interface, any specification implementing at least the provided interface (technical name of the interface) will
	 * be considered satisfactory.
	 * If found, the instance(s) are bound is returned.
	 *
	 * @param client the instance that requires the specification
	 * @param depName the dependency name. Field for atomic; spec name for complex dep, type for composite.
	 * @return
	 */
	@Override
	public boolean resolveWire(Instance client, String depName) {
		logger.info("Resolving dependency " + depName + " from instance " + client.getName() );
		if ((depName == null) || (client == null)) {
			logger.error("missing client or dependency name");
			return false;
		}

		//compute the set of constraints that apply to that resolution: inst + impl + spec + composite generique
		DependencyDeclaration dependency = Util.computeEffectiveDependency (client, depName) ;

		if (dependency == null) {
			logger.error("dependency declaration invalid or not found " + depName);
			return false;
		}


		Set<Instance> insts = null ;
		Implementation impl = null;
		//will contain all the implementations satisfying the constraints (visibility not checked)
		Set<Implementation> impls = new HashSet<Implementation> ();

		/*
		 *  Promotion control
		 */
		Composite compo = getClientComposite(client);
		Instance refClient = client;
		DependencyDeclaration promotionDependency = getPromotion(client, dependency);
		// if it is a promotion, visibility and scope is the one of the embedding composite.
		if (promotionDependency != null) {
			refClient = compo ;
			compo = compo.getComposite();
			Set<Instance> promoInsts = client.getComposite().getWireDests(promotionDependency.getIdentifier());
			if (!promoInsts.isEmpty()) {
				insts = promoInsts ;
				impls.add(insts.iterator().next().getImpl ());
				logger.info("Selected from promotion " + insts);
			}
		}

		/*
		 * Try to find the instances.
		 */
		if (insts == null) {
			// Look for the implementation(s)
			//A direct implementation dependency
			if (dependency.getTarget() instanceof ImplementationReference) {
				impl = findImplByName(refClient, dependency.getTarget().getName());
				if (impl != null) impls.add(impl) ;
				insts = resolveImpls(refClient, impl, dependency) ;
			} else {
				insts = new HashSet <Instance> () ;
				//insts loaded here
				impls = this.resolveSpecByResources(refClient, dependency, insts);
			}
		}
		if ((impls == null || impls.isEmpty()) && insts.isEmpty()) {
			logger.error("Failed to resolve " + dependency.getTarget()
					+ " from " + client + "(" + depName + ")");
			return false;
		}

		/*
		 * We have all the visible implems, and all the existing visible instances
		 * satisfying the constraints.
		 * WARNING: impls can be null and insts not ; and vice-versa
		 * If No existing instance. Create one.
		 */
		if (insts.isEmpty()) {
			impl = Select.getPrefered(impls, Util.toFilterList(dependency.getImplementationPreferences())) ;
			Instance inst = impl.createInstance(compo, null);
			if (inst == null){// may happen if impl is non instantiable
				logger.error("Failed creating instance of " + impl );
				return false;
			}

			insts.add(inst);
			impls.clear () ;
			impls.add(impl) ;
			logger.info("Instantiated " + inst);
		} else
			logger.info("selected : " + insts) ;

		/*
		 * If dependency is not multiple, select the best instance and implem.
		 * Return a single element in both impls and insts
		 */
		if (!dependency.isMultiple()) {
			Instance inst = Select.selectBestInstance (impls, insts, dependency) ;
			insts.clear();
			insts.add(inst) ;
		}

		/*
		 *  We got the instances. Create the wires.
		 *  the set is a singleton if multiple = false.
		 */
		boolean ok = false ;

		//If the dependency has constraints, the wire has to be re-evaluated when a provider property changes.
		boolean hasConstraints = (!dependency.getImplementationConstraints().isEmpty() || !dependency.getInstanceConstraints().isEmpty()) ;

		for (Instance inst : insts) {
			deployedImpl(refClient, inst.getImpl(), false);
			// For promotions we must wire the composite and wire the client if the target matches the client constraints
			if (promotionDependency != null) { // it was a promotion, embedding composite must be linked as the source

				//If the composite dependency has constraints, the wire has to be re-evaluated when a provider property changes.
				boolean compoHasConstraints = (!promotionDependency.getImplementationConstraints().isEmpty()
						|| !promotionDependency.getInstanceConstraints().isEmpty()) ;
				//if the composite dependency changes, the promoted dependency must be changed too !!
				hasConstraints = hasConstraints || compoHasConstraints ;

				client.getComposite().createWire(inst, promotionDependency.getIdentifier(), compoHasConstraints, false);
				logger.info("Promoting " + client + " -" + depName + "-> " + inst + "\n      as: "
						+ client.getComposite() + " -" + promotionDependency.getIdentifier() + "-> " + inst);

				// wire the client only if the composite target matches the client constraints
				if (inst.getImpl().match(Util.toFilter(dependency.getImplementationConstraints()))
						&& inst.match(Util.toFilter(dependency.getInstanceConstraints()))) {
					client.createWire(inst, depName, hasConstraints, true);
					ok = true ;
				}
			}

			/*
			 * It is not a promotion. Normal case: we wire the client toward all the instances.
			 */
			else 	{
				client.createWire(inst, depName, hasConstraints, false);
				ok = true ;
			}
		}

		// notify the managers
		ApamResolverImpl.notifySelection(client, dependency.getTarget(), depName,
				((Instance) insts.toArray()[0]).getImpl(), null, insts);
		return ok;
	}


	/**
	 * Before to resolve an implementation (i.e. to select one of its instance), this method is called to
	 * know which managers are involved, and what are the constraints and preferences set by the managers to this
	 * resolution.
	 *
	 * @param compTypeFrom : the origin of this resolution.
	 * @param impl : the implementation to resolve.
	 * @param constraints : the constraints added by the managers. A (empty) set must be provided as parameter.
	 * @param preferences : the preferences added by the managers. A (empty) list must be provided as parameter.
	 * @return : the managers that will be called for that resolution.
	 */
	private List<DependencyManager> computeSelectionPath(Instance client, DependencyDeclaration dependency) {

		List<DependencyManager> selectionPath = new ArrayList<DependencyManager>();
		for (DependencyManager dependencyManager : ApamManagers.getManagers()) {
			/*
			 * Skip apamman
			 */
			if (dependencyManager.getName().equals(CST.APAMMAN) || dependencyManager.getName().equals(CST.UPDATEMAN))
				continue;
			dependencyManager.getSelectionPath(client, dependency,selectionPath);
		}

		// To select first in Apam
		selectionPath.add(0, apam.getApamMan());
		selectionPath.add(0, apam.getUpdateMan());
		return selectionPath;
	}

	/**
	 * Impl has been deployed, it becomes embedded in compoType.
	 * If physically deployed, it is in the Unused list. remove.
	 *
	 * @param compoType
	 * @param impl
	 */
	private static void deployedImpl(Instance client, Implementation impl, boolean deployed) {
		CompositeType compoType = client.getComposite().getCompType();
		// it was not deployed
		if (!deployed && impl.isUsed()) {
			logger.info(" : selected " + impl);
			return;
		}

		// it is deployed or was never used so far
		if (impl.isUsed()) {
			logger.info(" : logically deployed " + impl);
		} else {// it was unused so far.
			((ComponentImpl)impl).setFirstDeployed(compoType);
			logger.info(" : deployed " + impl);
		}
		((CompositeTypeImpl)compoType).deploy(impl);
	}

	/**
	 * Look for an implementation with a given dependency, visible from composite Type compoType.
	 *
	 * @param compoType
	 * @param componentName
	 * @return
	 */
	public Implementation findImplByDependency (Instance client, DependencyDeclaration dep) {
		if (dep == null) return null;
		List<DependencyManager> selectionPath = computeSelectionPath(client, dep);

		Implementation impl = null;
		logger.info("Looking for implementation " + dep.getTarget().getName() + ": ");
		boolean deployed = false;
		for (DependencyManager manager : selectionPath) {
			if (!manager.getName().equals(CST.APAMMAN) && !manager.getName().equals(CST.UPDATEMAN))
				deployed = true;
			logger.debug(manager.getName() + "  ");
			impl = manager.findImplByDependency(client, dep);
			if (impl != null) {
				deployedImpl(client, impl, deployed);
				return impl ;
			}
		}
		return null;
	}

	/**
	 * Look for an implementation with a given name "implName", visible from composite Type compoType.
	 *
	 * @param compoType
	 * @param componentName
	 * @return
	 */

	private <C extends Component> C findByName (Instance client, String componentName, Class<C> kind /*, Composite composite*/) {
		if (componentName == null) return null;
		if (client == null)
			client = CompositeImpl.getRootInstance();

		CompositeType compoType = CompositeTypeImpl.getRootCompositeType();
		DependencyDeclaration dep = new DependencyDeclaration (compoType.getImplDeclaration().getReference(),
				componentName, false, new ComponentReference<ComponentDeclaration>(componentName)) ; //new ImplementationReference<ImplementationDeclaration>(componentName));

		List<DependencyManager> selectionPath = computeSelectionPath(client, dep);

		Component compo = null;
		logger.info("Looking for component " + componentName + ": ");
		boolean deployed = false;
		for (DependencyManager manager : selectionPath) {
			if (!manager.getName().equals(CST.APAMMAN) && !manager.getName().equals(CST.UPDATEMAN))
				deployed = true;
			logger.debug(manager.getName() + "  ");
//			if (kind == Instance.class)
//				compo = manager.findInstByName(client, componentName);
//			else
				compo = manager.findComponentByName(client, componentName);

			if (compo != null) { //We found it, but maybe the wrong type
				if (! kind.isAssignableFrom(compo.getClass())) {
					logger.error ("Component " + componentName + " found but not a " + kind.getName()) ;
					return null ;
				}

				if (compo instanceof Implementation) {
					deployedImpl(client, (Implementation)compo, deployed);
				}
				return kind.cast(compo) ;//(C)compo;
			}
		}
		return null;
	}


	@Override
	public fr.imag.adele.apam.Component findComponentByName(Instance client, String componentName) {
		return findByName (client, componentName, fr.imag.adele.apam.Component.class) ;
	}

	@Override
	public Specification findSpecByName(Instance client, String specName) {
		return findByName (client, specName, fr.imag.adele.apam.Specification.class) ;
	}

	@Override
	public Implementation findImplByName(Instance client, String implName) {
		return findByName (client, implName, fr.imag.adele.apam.Implementation.class) ;
	}

	public Instance findInstByName(Instance client, String instName) {
		return findByName (client, instName, fr.imag.adele.apam.Instance.class) ;
	}


	/**
	 * First looks for the specification defined by its name, and then resolve that specification.
	 * Returns the implementation that implement the specification and that satisfies the constraints.
	 *
	 * @param compoType : the implementation to return must either be visible from compoType, or be deployed.
	 * @param specName
	 * @param constraints. The constraints to satisfy. They must be all satisfied.
	 * @param preferences. If more than one implementation satisfies the constraints, returns the one that satisfies the
	 *            maximum
	 *            number of preferences, taken in the order, and stopping at the first failure.
	 * @return
	 */
	@Override
	public Implementation resolveSpecByName(Instance client, String specName,
			Set<String> constraints, List<String> preferences) {
		if (constraints == null)
			constraints = new HashSet<String>();
		if (preferences == null)
			preferences = new ArrayList<String>();
		if (client == null)
			client = CompositeImpl.getRootInstance () ;
		CompositeType compoTypeFrom = client.getComposite().getCompType();

		DependencyDeclaration dep = new DependencyDeclaration (compoTypeFrom.getImplDeclaration().getReference(), specName, false, new SpecificationReference(specName));
		dep.getImplementationConstraints().addAll(constraints) ;
		dep.getImplementationPreferences().addAll(preferences) ;

		List<DependencyManager> selectionPath = computeSelectionPath(client, dep);

		if (constraints.isEmpty() && preferences.isEmpty())
			logger.info("Looking a \"" + specName + "\" implementation.");
		else
			logger.info("Looking a \"" + specName + "\" implementation. Constraints:" + constraints
					+ ". Preferences: " + preferences);
		boolean deployed = false;
		for (DependencyManager manager : selectionPath) {
			if (!manager.getName().equals(CST.APAMMAN) && !manager.getName().equals(CST.UPDATEMAN))
				deployed = true;
			logger.debug(manager.getName() + "  ");
			Implementation impl = manager.resolveSpec(client, dep) ;

			if (impl != null) {
				deployedImpl(client, impl, deployed);
				return impl;
			}
		}
		return null;
	}

	/**
	 * First looks for the specification defined by its interface, and then resolve that specification.
	 * Returns the implementation that implement the specification and that satisfies the constraints.
	 *
	 * @param compoType : the implementation to return must either be visible from compoType, or be deployed.
	 * @param interfaceName. The full name of one of the interfaces of the specification.
	 *            WARNING : different specifications may share the same interface.
	 * @param interfaces. The complete list of interface of the specification. At most one specification can be
	 *            selected.
	 * @param constraints. The constraints to satisfy. They must be all satisfied.
	 * @param preferences. If more than one implementation satisfies the constraints, returns the one that satisfies the
	 *            maximum
	 *            number of preferences, taken in the order, and stopping at the first failure.
	 * @return
	 */
	public Implementation resolveSpecByResource(Instance client, DependencyDeclaration dependency) {

		logger.info("Looking for an implem with" + dependency);
		if (client == null)
			client = CompositeImpl.getRootInstance();

		List<DependencyManager> selectionPath = computeSelectionPath(client, dependency);        
		Implementation impl = null;
		boolean deployed = false;
		for (DependencyManager manager : selectionPath) {
			if (!manager.getName().equals(CST.APAMMAN) && !manager.getName().equals(CST.UPDATEMAN))
				deployed = true;
			logger.debug(manager.getName() + "  ");
			impl = manager.resolveSpec(client, dependency);
			if (impl != null) {
				deployedImpl(client, impl, deployed);
				return impl;
			}
		}
		return null;
	}

	public Set<Implementation> resolveSpecByResources(Instance client, DependencyDeclaration dependency, Set<Instance> insts) {

		logger.info("Looking for all implems with" + dependency);
		if (client == null)
			client = CompositeImpl.getRootInstance();
		List<DependencyManager> selectionPath = computeSelectionPath(client, dependency);

		Set<Implementation> impls = null;
		boolean deployed = false;
		for (DependencyManager manager : selectionPath) {
			if (!manager.getName().equals(CST.APAMMAN) && !manager.getName().equals(CST.UPDATEMAN))
				deployed = true;
			logger.debug(manager.getName() + "  ");
			impls = manager.resolveSpecs(client, dependency, insts);
			if (impls != null && !impls.isEmpty() && deployed) {
				for (Implementation impl : impls) {
					deployedImpl(client, impl, deployed);
				}
				return impls ;
			}
		}
		return null;
	}

	/**
	 * Look for an instance of "impl" that satisfies the constraints. That instance must be either
	 * - shared and visible from "compo", or
	 * - instantiated if impl is visible from the composite type.
	 *
	 * @param compo. the composite that will contain the instance, if created, or from which the shared instance is
	 *            visible.
	 * @param impl
	 * @param constraints. The constraints to satisfy. They must be all satisfied.
	 * @param preferences. If more than one implementation satisfies the constraints, returns the one that satisfies the
	 *            maximum
	 * @return
	 */
	public Instance resolveImpl(Instance client, Implementation impl, DependencyDeclaration dependency) {
		if (client == null)
			client = CompositeImpl.getRootInstance();

		List<DependencyManager> selectionPath = computeSelectionPath(client, dependency);

		Instance inst = null;
		logger.info("Looking for an instance of " + impl + ": ");
		for (DependencyManager manager : selectionPath) {
			logger.debug(manager.getName() + "  ");
			inst = manager.resolveImpl(client, impl, dependency);
			if (inst != null) {
				return inst;
			}
		}
		return null;
	}

	/**
	 * Look for all the existing instance of "impl" that satisfy the constraints.
	 * These instances must be either shared and visible from "compo".
	 * If no existing instance can be found, one is created if impl is visible from the composite type.
	 *
	 * @param compo. the composite that will contain the instance, if created, or from which the shared instance is
	 *            visible.
	 * @param impl
	 * @param constraints. The constraints to satisfy. They must be all satisfied.
	 * @return
	 */
	public Set<Instance> resolveImpls(Instance client, Implementation impl, DependencyDeclaration dependency) {
		if (client == null)
			client = CompositeImpl.getRootInstance();

		List<DependencyManager> selectionPath = computeSelectionPath(client, dependency);

		Set<Instance> insts = null;
		logger.info("Looking for instances of " + impl + ": ");
		for (DependencyManager manager : selectionPath) {
			logger.debug(manager.getName() + "  ");
			insts = manager.resolveImpls(client, impl, dependency);
			if ((insts != null) && !insts.isEmpty()) {
				logger.debug("selected " + insts);
				return insts;
			}
		}
		return Collections.emptySet();

	}

	/**
	 * Once the resolution terminated, either successful or not, the managers are notified of the current
	 * selection.
	 * Currently, the managers cannot "undo" nor change the current selection.
	 *
	 * @param spec
	 * @param impl
	 * @param inst
	 * @param insts
	 */
	private static void notifySelection(Instance client, ResolvableReference resName, String depName,
			Implementation impl, Instance inst, Set<Instance> insts) {
		for (DependencyManager dependencyManager : ApamManagers.getManagers()) {
			dependencyManager.notifySelection(client, resName, depName, impl, inst, insts);
		}
	}


	@Override
	public Implementation resolveSpecByInterface(Instance client,
			String interfaceName, Set<String> constraints,
			List<String> preferences) {
		DependencyDeclaration dep = new DependencyDeclaration (client.getComposite().getCompType().getDeclaration().getReference(),
				interfaceName, false, new InterfaceReference(interfaceName));

		dep.getImplementationConstraints().addAll(constraints) ;
		dep.getImplementationPreferences().addAll(preferences) ;
		return resolveSpecByResource(client, dep);
	}

	@Override
	public Implementation resolveSpecByMessage(Instance client,
			String messageName, Set<String> constraints, List<String> preferences) {

		DependencyDeclaration dep = new DependencyDeclaration (client.getComposite().getCompType().getDeclaration().getReference(),
				messageName, false, new MessageReference(messageName));

		dep.getImplementationConstraints().addAll(constraints) ;
		dep.getImplementationPreferences().addAll(preferences) ;
		return resolveSpecByResource(client, dep);
	}

	@Override
	public Instance resolveImpl(Instance client, Implementation impl,
			Set<String> constraints, List<String> preferences) {

		DependencyDeclaration dep = new DependencyDeclaration (client.getComposite().getCompType().getDeclaration().getReference(),
				impl.getName(), false, new ImplementationReference<ImplementationDeclaration>(impl.getName()));

		dep.getImplementationConstraints().addAll(constraints) ;
		dep.getImplementationPreferences().addAll(preferences) ;

		return resolveImpl(client, impl, dep);
	}

	@Override
	public Set<Instance> resolveImpls(Instance client, Implementation impl, Set<String> constraints) {

		DependencyDeclaration dep = new DependencyDeclaration (client.getComposite().getCompType().getDeclaration().getReference(),
				impl.getName(), true, new ImplementationReference<ImplementationDeclaration>(impl.getName()));

		dep.getImplementationConstraints().addAll(constraints) ;

		return resolveImpls(client, impl, dep);
	}

	@Override
	public void updateComponent(String componentName) {
		Implementation impl = CST.componentBroker.getImpl(componentName) ;
		if (impl == null) {
			logger.error ("Unknown component " + componentName) ;
			return ;
		}
		UpdateMan.updateComponent(impl) ;
	}


}