package fr.imag.adele.apam.apamMavenPlugin;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.imag.adele.apam.CST;
import fr.imag.adele.apam.core.AtomicImplementationDeclaration;
import fr.imag.adele.apam.core.ComponentDeclaration;
import fr.imag.adele.apam.core.ComponentReference;
import fr.imag.adele.apam.core.CompositeDeclaration;
import fr.imag.adele.apam.core.DependencyDeclaration;
import fr.imag.adele.apam.core.DependencyInjection;
import fr.imag.adele.apam.core.InterfaceReference;
import fr.imag.adele.apam.core.MessageReference;
import fr.imag.adele.apam.core.ResourceReference;
import fr.imag.adele.apam.core.SpecificationReference;
import fr.imag.adele.apam.util.ApamFilter;
import fr.imag.adele.apam.util.Util;

public class CheckObr {

	private static Logger logger = LoggerFactory.getLogger(CheckObr.class);

	private static final Set<String>             allFields         = new HashSet<String>();

	private static boolean                       failedChecking = false;

	/**
	 * An string value that will be used to represent mandatory attributes not specified. From CoreParser.
	 */
	public final static String                   UNDEFINED      = new String("<undefined value>");


	public static boolean getFailedChecking() {
		return CheckObr.failedChecking;
	}

	public static void error(String msg) {
		CheckObr.failedChecking = true;
		logger.error("ERROR: " + msg);
	}

	public static void warning(String msg) {
		logger.error("Warning: " + msg);
	}

	public static void setFailedParsing(boolean failed) {
		CheckObr.failedChecking = failed;
	}

	/**
	 * Checks if the constraints set on this dependency are syntacticaly valid.
	 * Only for specification dependencies.
	 * Checks if the attributes mentioned in the constraints can be set on an implementation of that specification.
	 * @param dep a dependency
	 */
	private static void checkConstraint(DependencyDeclaration dep) {
		if ((dep == null) || !(dep.getTarget() instanceof ComponentReference))
			return;

		//get the spec or impl definition
		ApamCapability cap = ApamCapability.get(dep.getTarget().as(ComponentReference.class));
		if (cap == null)
			return;
		
		//computes the attributes that can be associated with this spec or implementations members
		Map<String, String> validAttrs = cap.getValidAttrNames();

		CheckObr.checkFilters(dep.getImplementationConstraints(), dep.getImplementationPreferences(), validAttrs, dep.getTarget().getName());
		CheckObr.checkFilters(dep.getInstanceConstraints(), dep.getInstancePreferences(), validAttrs, dep.getTarget().getName());
	}


	private static void checkFilters(Set<String> filters, List<String> listFilters, Map<String, String> validAttr, String comp) {
		if (filters != null) {
			for (String f : filters) {
				ApamFilter parsedFilter = ApamFilter.newInstance(f);
				parsedFilter.validateAttr(validAttr, f, comp);
			}
		}
		if (listFilters != null) {
			for (String f : listFilters) {
				ApamFilter parsedFilter = ApamFilter.newInstance(f);
				parsedFilter.validateAttr(validAttr, f, comp);
			}
		}
	}


	/**
	 * Checks the attributes defined in the component; 
	 * if valid, they are returned.
	 * Then the attributes pertaining to the entity above are added.
	 * @param component the component to check
	 */
	public static Map<String, String> getValidProperties(ComponentDeclaration component) {
		//the attributes to return
		Map<String, String> ret = new HashMap <String, String> ();
		//Properties of this component
		Map<String, String> properties = component.getProperties();

		ApamCapability entCap = ApamCapability.get(component.getReference()) ;
		if (entCap == null) return ret ; //should never happen.
		
		//return the valid attributes
		for (String attr : properties.keySet()) {
			if (validDefObr (entCap, attr, properties.get(attr))) {
				ret.put(attr, properties.get(attr)) ;
			}
		}
		
		//add the attribute coming from "above" if not already instantiated and heritable
		ApamCapability group = entCap.getGroup() ;
		if (group != null && group.getProperties()!= null) {
			for (String prop : group.getProperties().keySet()) {
				if (ret.get(prop) == null && Util.isInheritedAttribute(prop)) {
					ret.put(prop, group.getProperties().get(prop)) ;
				}			
			}
		}	
		
		/*
		 * Add the default values specified in the group for properties not
		 * explicitly initialized
		 */
		if (group != null) {
			for (String prop : group.getValidAttrNames().keySet()) {
				if (! Util.isInheritedAttribute(prop)) 
					continue;
				if ( ret.get(prop) != null )
					continue;
				if (group.getAttrDefault(prop) == null)
					continue;
				
				ret.put(prop, group.getAttrDefault(prop)) ;
			}
		} 
		
		return ret ;
	}

	/**
	 * Checks if the attribute / values pair is valid for the component ent.
	 * 
	 * @param entName
	 * @param attr
	 * @param value
	 * @param groupProps
	 * @param superGroupProps
	 * @return
	 */
	private static boolean validDefObr (ApamCapability ent, String attr, String value) {
		if (Util.isPredefinedAttribute(attr))return true ; ;
		if (!Util.validAttr(ent.getName(), attr)) return false  ;
		
//		//Top group all is Ok
//		ApamCapability group = ent.getGroup() ;
//		if (group == null) return true ;
		
		if (ent.getGroup()!= null && ent.getGroup().getProperties().get(attr) != null)  {
			warning("Cannot redefine attribute \"" + attr + "\"");
			return false ;
		}

		String defAttr = null ;
		//if we are at top level, the attribute definition is at the same level; otherwise it must be defined "above"
		ApamCapability group = (ent.getGroup() == null) ? ent : ent.getGroup() ;
		while (group != null) {
			defAttr = group.getAttrDefinition(attr)  ;
			if (defAttr != null) break ;
			group = group.getGroup() ;
		}
		 
		if (defAttr == null) {
			warning("In " + ent.getName() + ", attribute \"" + attr + "\" used but not defined.");
			return false ;
		}

		return Util.checkAttrType(attr, value, defAttr) ;		
	}


	/**
	 * An implementation has the following provide; check if consistent with the list of provides found in "cap".
	 * 
	 * @param cap. Can be null.
	 * @param interfaces = "{I1, I2, I3}" or I1 or null
	 * @param messages= "{M1, M2, M3}" or M1 or null
	 * @return
	 */
	public static boolean checkImplProvide(String implName, String spec, Set<InterfaceReference> interfaces,
			Set<MessageReference> messages) {
		if (spec == null)
			return true;
		ApamCapability cap = ApamCapability.get(new SpecificationReference(spec));
		if (cap == null) {
			return true;
		}

		Set<MessageReference> specMessages = cap.getProvideMessages();
		Set<InterfaceReference> specInterfaces = cap.getProvideInterfaces();

		if (!(messages.containsAll(specMessages)))
			CheckObr.error("Implementation " + implName + " must produce messages "
					+ Util.toStringSetReference(specMessages)) ;

		if (!(interfaces.containsAll(specInterfaces)))
			CheckObr.error("Implementation " + implName + " must implement interfaces "
					+ Util.toStringSetReference(specInterfaces)) ;

		return true;
	}


	public static void checkCompoMain(CompositeDeclaration composite) {
		String name = composite.getName();
		String implName = composite.getMainComponent().getName();
		ApamCapability cap = ApamCapability.get(composite.getMainComponent());
		if (cap == null) {
			return;
		}
		if (composite.getSpecification() != null) {
			String spec = composite.getSpecification().getName();
			String capSpec = cap.getProperty(CST.A_PROVIDE_SPECIFICATION);
			if ((capSpec != null) && !spec.equals(capSpec)) {
				CheckObr.error("In " + name + " Invalid main implementation. " + implName
						+ " must implement specification " + spec);
			}
		}

		Set<MessageReference> mainMessages = cap.getProvideMessages();
		Set<MessageReference> compositeMessages = composite.getProvidedResources(MessageReference.class);
		if (!mainMessages.containsAll(compositeMessages))
			CheckObr.error("In " + name + " Invalid main implementation. " + implName
					+ " produces messages " + mainMessages
					+ " \n but must produce messages " + compositeMessages);

		Set<InterfaceReference> mainInterfaces = cap.getProvideInterfaces() ;
		Set<InterfaceReference> compositeInterfaces = composite.getProvidedResources(InterfaceReference.class);
		if (!mainInterfaces.containsAll(compositeInterfaces))
			CheckObr.error("In " + name + " Invalid main implementation. " + implName
					+ " implements " + mainInterfaces
					+ " \n but must implement interfaces " + compositeInterfaces);
	}

	/**
	 * For all kinds of components checks the dependencies : fields (for implems), and constraints.
	 * 
	 * @param component
	 */
	public static void checkRequire(ComponentDeclaration component) {
		Set<DependencyDeclaration> deps = component.getDependencies();
		if (deps == null)
			return;
		CheckObr.allFields.clear();
		Set<String> depIds = new HashSet<String>();
		for (DependencyDeclaration dep : deps) {
			if (depIds.contains(dep.getIdentifier())) {
				CheckObr.error("Dependency " + dep.getIdentifier() + " allready defined.");
			} else
				depIds.add(dep.getIdentifier());
			// validating dependency constraints and preferences..
			CheckObr.checkConstraint(dep);
			// Checking fields and complex dependencies
			CheckObr.checkFieldTypeDep(dep, component);
		}
	}




	/**
	 * Provided a dependency "dep" (simple or complex) checks if the field type and attribute multiple are compatible.
	 * For complex dependency, for each field, checks if the target specification implements the field resource.
	 * 
	 * @param dep : a dependency
	 * @param component : the component currently analyzed
	 */
	private static void checkFieldTypeDep(DependencyDeclaration dep, ComponentDeclaration component) {
		if (!(component instanceof AtomicImplementationDeclaration)) return ;

		// All field must have same multiplicity, and must refer to interfaces and messages provided by the specification.

		Set<ResourceReference> specResources = new HashSet<ResourceReference>();
		
		if (dep.getTarget() instanceof ComponentReference<?>) {
			ApamCapability cap = ApamCapability.get((ComponentReference)dep.getTarget()) ;
			if (cap == null) return ;
			specResources = cap.getProvideResources() ;
		}
		else {
			specResources.add(dep.getTarget().as(ResourceReference.class));
		}
	
		for (DependencyInjection innerDep : dep.getInjections()) {
			
			String type = innerDep.getResource().getJavaType();

			if ((innerDep.getResource() != ResourceReference.UNDEFINED) && !(specResources.contains(innerDep.getResource()))) {
				CheckObr.error("In " + component.getName() + dep + "\n      Field "
						+ innerDep.getName()
						+ " is of type " + type
						+ " which is not implemented by specification or implementation " + dep.getIdentifier());
			}
		}
	}

	/**
	 * Provided an atomic dependency, returns if it is multiple or not.
	 * Checks if the same field is declared twice.
	 * 
	 * @param dep
	 * @param component
	 * @return
	 */
	public static boolean isFieldMultiple(DependencyInjection dep, ComponentDeclaration component) {
		if (CheckObr.allFields.contains(dep.getName()) && !dep.getName().equals(CheckObr.UNDEFINED)) {
			CheckObr.error("In " + component.getName() + " field/method " + dep.getName()
					+ " allready declared");
		}
		else {
			CheckObr.allFields.add(dep.getName());
		}

		return dep.isCollection();
	}
	
}
