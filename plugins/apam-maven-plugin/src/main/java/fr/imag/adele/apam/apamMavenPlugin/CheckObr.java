/**
 * Copyright 2011-2012 Universite Joseph Fourier, LIG, ADELE team
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
package fr.imag.adele.apam.apamMavenPlugin;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.imag.adele.apam.AttrType;
import fr.imag.adele.apam.CST;
import fr.imag.adele.apam.declarations.AtomicImplementationDeclaration;
import fr.imag.adele.apam.declarations.ComponentDeclaration;
import fr.imag.adele.apam.declarations.ComponentReference;
import fr.imag.adele.apam.declarations.CompositeDeclaration;
import fr.imag.adele.apam.declarations.ConstrainedReference;
import fr.imag.adele.apam.declarations.DependencyDeclaration;
import fr.imag.adele.apam.declarations.DependencyInjection;
import fr.imag.adele.apam.declarations.DependencyPromotion;
import fr.imag.adele.apam.declarations.GrantDeclaration;
import fr.imag.adele.apam.declarations.ImplementationDeclaration;
import fr.imag.adele.apam.declarations.ImplementationReference;
import fr.imag.adele.apam.declarations.InstanceDeclaration;
import fr.imag.adele.apam.declarations.InterfaceReference;
import fr.imag.adele.apam.declarations.MessageReference;
import fr.imag.adele.apam.declarations.OwnedComponentDeclaration;
import fr.imag.adele.apam.declarations.PropertyDefinition;
import fr.imag.adele.apam.declarations.ResolvableReference;
import fr.imag.adele.apam.declarations.ResourceReference;
import fr.imag.adele.apam.declarations.SpecificationDeclaration;
import fr.imag.adele.apam.declarations.SpecificationReference;
import fr.imag.adele.apam.declarations.UndefinedReference;
import fr.imag.adele.apam.declarations.VisibilityDeclaration;
import fr.imag.adele.apam.util.Attribute;
import fr.imag.adele.apam.util.Substitute;
import fr.imag.adele.apam.util.Substitute.SplitSub;
import fr.imag.adele.apam.util.ApamFilter;
import fr.imag.adele.apam.util.Util;
import fr.imag.adele.apam.util.UtilComp;

public class CheckObr {

	private static Logger logger = LoggerFactory.getLogger(CheckObr.class);

	private static final Set<String> allFields = new HashSet<String>();
	private static final Set<String> allGrants = new HashSet<String>();
	private static boolean failedChecking = false;

	/**
	 * An string value that will be used to represent mandatory attributes not
	 * specified. From CoreParser.
	 */
	public final static String UNDEFINED = new String("<undefined value>");

	public static boolean getFailedChecking() {
		return CheckObr.failedChecking;
	}

	public static void error(String msg) {
		CheckObr.failedChecking = true;
		logger.error(msg);
	}

	public static void warning(String msg) {
		logger.warn(msg);
	}

	public static void setFailedParsing(boolean failed) {
		CheckObr.failedChecking = failed;
	}

	/**
	 * Checks if the constraints set on this dependency are syntacticaly valid.
	 * Only for specification dependencies. Checks if the attributes mentioned
	 * in the constraints can be set on an implementation of that specification.
	 * 
	 * @param dep a dependency
	 */
	private static void checkConstraint(ComponentDeclaration component, DependencyDeclaration dep) {
		if ((dep == null) || !(dep.getTarget() instanceof ComponentReference))
			return;

		// get the spec or impl definition
		ApamCapability cap = ApamCapability.get(dep.getTarget().as(
				ComponentReference.class));
		if (cap == null)
			return;

		// computes the attributes that can be associated with this spec or
		// implementations members
		Map<String, String> validAttrs = cap.getValidAttrNames();

		if (dep.isMultiple()
				&& (!dep.getImplementationPreferences().isEmpty() || !dep
						.getInstancePreferences().isEmpty())) {
			error("Preferences cannot be defined for a dependency with multiple cardinality: "
					+ dep.getIdentifier());
		}
		checkFilters(component, dep.getImplementationConstraints(), dep.getImplementationPreferences(), 
				validAttrs, dep.getTarget().getName());
		checkFilters(component, dep.getInstanceConstraints(), dep.getInstancePreferences(), 
				validAttrs, dep.getTarget().getName());
	}

	/**
	 * Checks the attributes *defined* in the component; if valid, they are
	 * returned. Then the attributes pertaining to the entity above are added.
	 * Then the final attributes
	 * 
	 * @param component
	 *            the component to check
	 */
	public static Map<String, Object> getValidProperties(
			ComponentDeclaration component) {
		// the attributes to return
		Map<String, Object> ret = new HashMap<String, Object>();
		// Properties of this component
		Map<String, String> properties = component.getProperties();

		ApamCapability entCap = ApamCapability.get(component.getReference());
		if (entCap == null)
			return ret; // should never happen.

		// return the valid attributes
		for (String attr : properties.keySet()) {
			String defAttr = getDefAttr(entCap, attr, properties.get(attr));
			if (defAttr == null)
				continue;
			Object val = Attribute.checkAttrType(attr, properties.get(attr), defAttr);
			if (val == null) {
				setFailedParsing(true);
				continue;
			}
			//checkSubstitute (ComponentDeclaration component, String attr, String type, String defaultValue) 
			//			if (checkSubstitute(component, attr, defAttr, properties.get(attr))) {
			ret.put(attr, val);
			//			}
		}

		// add the attribute coming from "above" if not already instantiated and
		// heritable
		ApamCapability group = entCap.getGroup();
		if (group != null && group.getProperties() != null) {
			for (String prop : group.getProperties().keySet()) {
				if (ret.get(prop) == null && Attribute.isInheritedAttribute(prop)) {
					ret.put(prop, group.getProperties().get(prop));
				}
			}
		}

		/*
		 * Add the default values specified in the group for properties not
		 * explicitly initialized
		 */
		if (group != null) {
			for (String prop : group.getValidAttrNames().keySet()) {
				if (!Attribute.isInheritedAttribute(prop))
					continue;
				if (ret.get(prop) != null)
					continue;
				if (group.getAttrDefault(prop) == null)
					continue;

				ret.put(prop, group.getAttrDefault(prop));
			}
		}

		/*
		 * Add the component characteristics as final attributes, only if
		 * explicitly defined. Needed to compile members.
		 */
		if (component.isDefinedInstantiable()) {
			ret.put(CST.INSTANTIABLE,
					Boolean.toString(component.isInstantiable()));
		}
		if (component.isDefinedSingleton()) {
			ret.put(CST.SINGLETON, Boolean.toString(component.isSingleton()));
		}
		if (component.isDefinedShared()) {
			ret.put(CST.SHARED, Boolean.toString(component.isShared()));
		}

		return ret;
	}

	public static boolean checkProperty (ComponentDeclaration component, String name, String type, String defaultValue) {
		if (defaultValue == null)
			defaultValue = "";

		ApamCapability group = ApamCapability.get(component.getGroupReference()) ;

		//redefinition check
		if (group != null) {
			String groupType = group.getAttrDefinition(name) ;
			if (groupType != null) {
				//Allowed only if defining a field, and it types are the same. Default values are not allowed above in that case.
				PropertyDefinition propDef = component.getPropertyDefinition(name) ;
				if (propDef == null) {
					CheckObr.error("Invalid property definition " + name );	
					return false ;
				}
				if  (propDef.getField() == null) {
					CheckObr.error ("Property " + name + " allready defined in the group.") ;
					return false ;
				} 
				if (! propDef.getType().equals(groupType)) {
					CheckObr.error("Cannot refine property definition " + name + " Not the same types : " + propDef.getType() + " not equal " + groupType );
					return false ;
				}
				if (group.getAttrDefault(name) != null) {
					CheckObr.error("Cannot refine property definition with a default value properties " + name + "=" + group.getAttrDefault(name));
					return false ;
				}
			}
		}

		//We have a default value, check it as if a property.
		if (type != null && defaultValue != null && !defaultValue.isEmpty()) {
			if (Attribute.checkAttrType(name, defaultValue, type) != null) {
				return checkSubstitute (component, name, type, defaultValue) ;
			} else {
				CheckObr.setFailedParsing(true) ;
				return false ;
			}
		}

		//no default value. Only check if the type is valid
		if (!Attribute.validAttrType (type)) {
			CheckObr.setFailedParsing(true) ; 
			return false ;
		}

		return true ;
	}

	private static boolean checkFunctionSubst (ComponentDeclaration component, String attr, String type, String defaultValue) {

		//		String func = defaultValue.substring(1) ;
		/*
		 * Add a function that checks if the public method "public <type> func (Instance inst)" is realy defined in the 
		 * class, and if its returned type is compatible with the "type"
		 */
		//TODO to test, I supposed it is Ok !
		// TODO if (       .contains(func) ....
		return true ;
	}

	/**
	 * Checks the syntax of the filter.
	 * Warning : does not check the values : their type and the substitutions.
	 * @param filter
	 * @return
	 */
	private static boolean checkSyntaxFilter(String filter) {
		try {
			ApamFilter parsedFilter = ApamFilter.newInstance(filter);
			return parsedFilter != null ;
		}
		catch (Exception e) {
			return false ;
		}
	}

	private static boolean isSubstitute (ComponentDeclaration component, String attr) {
		PropertyDefinition def = component.getPropertyDefinition(attr) ;
		return  (def != null && (def.getDefaultValue().charAt(0)=='$' || def.getDefaultValue().charAt(0)=='@')) ;
	}

	private static boolean checkFilter(ApamFilter filt, ComponentDeclaration component, Map<String, String> validAttr, String f, String spec) {
		switch (filt.op) {
		case ApamFilter.AND:
		case ApamFilter.OR: {
			ApamFilter[] filters = (ApamFilter[]) filt.value;
			boolean ok = true ;
			for (ApamFilter filter : filters) {
				if (!checkFilter(filter, component, validAttr, f, spec)) ok = false ;
			}
			return ok;
		}

		case ApamFilter.NOT: {
			ApamFilter filter = (ApamFilter) filt.value;
			return checkFilter(filter, component, validAttr, f, spec);
		}

		case ApamFilter.SUBSTRING:
		case ApamFilter.EQUAL:
		case ApamFilter.GREATER:
		case ApamFilter.LESS:
		case ApamFilter.APPROX:
		case ApamFilter.SUBSET:
		case ApamFilter.SUPERSET:
		case ApamFilter.PRESENT: {
			if (!Attribute.isFinalAttribute(filt.attr) && !validAttr.containsKey(filt.attr)) {
				logger.error("Members of component " + spec + " cannot have property " + filt.attr
						+ ". Invalid constraint " + f);
				return false ;
			}
			if (validAttr.containsKey(filt.attr)) {
				if (isSubstitute (component, filt.attr)) {
					error("Filter attribute  " +  filt.attr + " is a substitution: .  Invalid constraint " + f);
					return false ;
				}
				//        			if (Attribute.checkAttrType(name, defaultValue, type) != null) {
				//        				return checkSubstitute (component, name, type, defaultValue) ;
				if (Attribute.checkAttrType(filt.attr, (String)filt.value, validAttr.get(filt.attr)) == null) {
					return false ;
				}
				return CheckObr.checkSubstitute (component, filt.attr, validAttr.get(filt.attr), (String)filt.value) ;
				//                    return Attribute.checkAttrType(attr, (String)value, validAttr.get(attr)) != null;
			}
		}
		return true ;
		}
		return true ;
	}


	private static boolean checkSubstitute (ComponentDeclaration component, String attr, String type, String defaultValue) {
		//If it is a function substitution
		if (defaultValue.charAt(0) == '@')
			return checkFunctionSubst(component, attr, type, defaultValue) ;

		//If it is not a substitution, do nothing
		if (defaultValue.charAt(0) != '$')
			return true ;

		/*
		 * String substitution
		 */
		SplitSub sub = Substitute.split(defaultValue);
		if (sub == null) {
			error("Invalid substitute value " + defaultValue + " for attribute " + attr) ;
			return false ;
		}

		AttrType st = new AttrType (type) ;

		ApamCapability source = ApamCapability.get(component.getName()) ;
		if (!sub.sourceName.equals("this")) {
			//Look for the source component
			source = ApamCapability.get(sub.sourceName) ;
			if (source == null) {
				error("Component " + sub.sourceName + " not found in substitution : " + defaultValue + " of attribute " + attr) ;
				return false ;
			}
		}  


		/*
		 * if we have a dependency, get the dependency target
		 */
		if (sub.depId != null) {			
			DependencyDeclaration depDcl = source.dcl.getDependency(sub.depId) ;
			if (depDcl == null) {
				error("Dependency " + sub.depId + " undefined for component " + source.getName()) ;
				return false ;
			}

			ComponentReference<?> targetComponent = depDcl.getTarget().as(ComponentReference.class) ;
			if (targetComponent == null) { //it is an interface or message target. Cannot check.
				warning(depDcl.getTarget().getName() + " is an interface or message. Substitution \"" + defaultValue + "\" cannot be checked for attribute " + attr ) ;
				return true ;
			}

			source = ApamCapability.get(targetComponent.getName()) ;
			if (source == null) {
				error("Component " + targetComponent.getName() + " not found in substitution : " + defaultValue + " of attribute " + attr) ;
				return false ;
			}
		}


		/*
		 * check if the attribute is defined and if types are compatibles.			
		 * 
		 */
		String pd =  source.getAttrDefinition(sub.attr)  ;
		if (pd == null) {
			error("Substitute attribute " + attr + "=" +defaultValue + ".  Undefined attribute " + sub.attr + " for component " + source.getName()) ;
			return false ;
		}
		if (Substitute.checkSubType (st, new AttrType (pd), attr, sub))
			return true ;
		setFailedParsing(true)  ;
		return false ;

	}
	/**
	 * Checks if the attribute / values pair is valid for the component ent. If
	 * a final attribute, it is ignored but returns false. (cannot be set).
	 * 
	 * For "integer" returns an Integer object, otherwise it is the string "value"
	 * 
	 * @param entName
	 * @param attr
	 * @param value
	 * @param groupProps
	 * @param superGroupProps
	 * @return
	 */
	private static String getDefAttr(ApamCapability ent, String attr,
			String value) {
		if (Attribute.isFinalAttribute(attr))
			return null;
		if (!Attribute.validAttr(ent.getName(), attr))
			return null;

		if (ent.getGroup() != null
				&& ent.getGroup().getProperties().get(attr) != null) {
			error("Cannot redefine attribute \"" + attr + "\"");
			return null;
		}

		String defAttr = ent.getAttrDefinition(attr);

		if (defAttr == null) {
			error("In " + ent.getName() + ", attribute \"" + attr
					+ "\" used but not defined.");
			return null;
		}
		return defAttr;
	}

	/**
	 * An implementation has the following provide; check if consistent with the
	 * list of provides found in "cap".
	 * 
	 * @param cap
	 *            . Can be null.
	 * @param interfaces
	 *            = "{I1, I2, I3}" or I1 or null
	 * @param messages
	 *            = "{M1, M2, M3}" or M1 or null
	 * @return
	 */
	public static boolean checkImplProvide(ComponentDeclaration impl,
			String spec, Set<InterfaceReference> interfaces,
			Set<MessageReference> messages,
			Set<UndefinedReference> interfacesUndefined,
			Set<UndefinedReference> messagesUndefined) {
		if (!(impl instanceof AtomicImplementationDeclaration))
			return true;

		if (spec == null)
			return true;
		ApamCapability cap = ApamCapability
				.get(new SpecificationReference(spec));
		if (cap == null) {
			return true;
		}

		Set<MessageReference> specMessages = cap.getProvideMessages();
		Set<InterfaceReference> specInterfaces = cap.getProvideInterfaces();

		if (!messages.containsAll(specMessages)) {
			if (!messagesUndefined.isEmpty()) {
				CheckObr.warning("Unable to verify message type at compilation time, this may cause errors at the runtime!"
						+ "\n make sure that "
						+ Util.toStringUndefinedResource(messagesUndefined)
						+ " are of the following message types "
						+ Util.toStringSetReference(specMessages));
			} else {
				CheckObr.error("Implementation " + impl.getName()
						+ " must produce messages "
						+ Util.toStringSetReference(specMessages));
			}
		}

		if (!interfaces.containsAll(specInterfaces)) {
			if (!(interfacesUndefined.isEmpty())) {
				CheckObr.warning("Unable to verify intefaces type at compilation time, this may cause errors at the runtime!"
						+ "\n make sure that "
						+ Util.toStringUndefinedResource(interfacesUndefined)
						+ " are of the following interface types "
						+ Util.toStringSetReference(specInterfaces));
			} else {
				CheckObr.error("Implementation " + impl.getName()
						+ " must implement interfaces "
						+ Util.toStringSetReference(specInterfaces));
			}
		} 

		return true;
	}

	private static Set<ResourceReference> getAllProvidedResources(ImplementationDeclaration composite) {
		ComponentDeclaration spec = null;
		Set<ResourceReference> specResources = null;
		Set<ResourceReference>returnResources;
		if (composite.getSpecification() != null)
			spec = ApamCapability.getDcl(composite.getSpecification());
		if (spec != null)
			specResources = spec.getProvidedResources();

		Set<ResourceReference> compoResources = composite.getProvidedResources();
		if (specResources != null) {
			returnResources = new HashSet<ResourceReference>(specResources);
		}
		else returnResources = new HashSet<ResourceReference>() ;

		if (compoResources != null) {
			returnResources.addAll(compoResources);
		}
		return returnResources;
	}

	/**
	 * Get the provided resources of a given kind, for example Services or
	 * Messages.
	 * 
	 * We use subclasses of ResourceReference as tags to identify kinds of
	 * resources. To add a new kind of resource a new subclass must be added.
	 * 
	 * Notice that we return a set of resource references but typed to
	 * particular subtype of references, the unchecked downcast is then safe at
	 * runtime.
	 */
	@SuppressWarnings("unchecked")
	public static <T extends ResourceReference> Set<T> getProvidedResources(
			Set<ResourceReference> resources, Class<T> kind) {
		Set<T> res = new HashSet<T>();
		for (ResourceReference resourceReference : resources) {
			if (kind.isInstance(resourceReference))
				res.add((T) resourceReference);
		}
		return res;
	}

	public static void checkCompoMain(CompositeDeclaration composite) {
		String name = composite.getName();

		Set<ResourceReference> allProvidedResources = getAllProvidedResources(composite);
		// Abstract composite have no main implem, but must not provide any
		// resource
		if (composite.getMainComponent() == null) {
			if (!getAllProvidedResources(composite).isEmpty()) {
				error("Composite "
						+ name
						+ " does not declare a main implementation, but provides resources "
						+ getAllProvidedResources(composite));
			}
			return;
		}

		String implName = composite.getMainComponent().getName();
		ApamCapability cap = ApamCapability.get(composite.getMainComponent());
		if (cap == null) {
			return;
		}
		if (composite.getSpecification() != null) {
			String spec = composite.getSpecification().getName();
			String capSpec = cap.getProperty(CST.PROVIDE_SPECIFICATION);
			if ((capSpec != null) && !spec.equals(capSpec)) {
				CheckObr.error("In " + name + " Invalid main implementation. "
						+ implName + " must implement specification " + spec);
			}
		}

		Set<MessageReference> mainMessages = cap.getProvideMessages();
		Set<MessageReference> compositeMessages = getProvidedResources(
				allProvidedResources, MessageReference.class);
		if (!mainMessages.containsAll(compositeMessages))
			CheckObr.error("In " + name + " Invalid main implementation. "
					+ implName + " produces messages " + mainMessages
					+ " instead of " + compositeMessages);

		Set<InterfaceReference> mainInterfaces = cap.getProvideInterfaces();
		Set<InterfaceReference> compositeInterfaces = getProvidedResources(
				allProvidedResources, InterfaceReference.class);
		if (!mainInterfaces.containsAll(compositeInterfaces))
			CheckObr.error("In " + name + " Invalid main implementation. "
					+ implName + " implements " + mainInterfaces
					+ " instead of " + compositeInterfaces);
	}


	/**
	 * Checks if a class or interface exists among the bundles read by Maven to compile.
	 * Note we cannot know 
	 * @param interf
	 * @return
	 */
	public static boolean checkInterfaceExist (String interf) {
		//Checking if the interface is existing
		//Can be "<Unavalable>" for generic collections, since it is not possible to get the type at compile time
		if (interf == null || interf.equals("<Unavailable>")) return true ;
		if (OBRGeneratorMojo.classpathDescriptor.getElementsHavingClass(interf) == null) {
			CheckObr.error("Provided Interface " + interf + " does not exist in your Maven dependencies") ;
			return false ;
		}
		return true ;
	}

	/**
	 * For all kinds of components checks the dependencies : fields (for
	 * implems), and constraints.
	 * 
	 * @param component
	 */
	public static void checkDependencies(ComponentDeclaration component) {
		Set<DependencyDeclaration> deps = component.getDependencies() ;	
		if (deps == null || deps.isEmpty())
			return;

		CheckObr.allFields.clear();
		Set<String> depIds = new HashSet<String>();

		for (DependencyDeclaration dep : deps) {

			//Checking for double dependency Id
			if (depIds.contains(dep.getIdentifier())) {
				CheckObr.error("Dependency " + dep.getIdentifier()
						+ " allready defined.");
			} else
				depIds.add(dep.getIdentifier());

			// validating dependency constraints and preferences..
			CheckObr.checkConstraint(component, dep);

			//replace the definition by the effective dependency (adding group definition)
			computeGroupDependency (component, dep);

			// Checking fields and complex dependencies
			CheckObr.checkFieldTypeDep(dep);

			//eager and hide cannot be defined here
			if (dep.isEager() != null || dep.isHide() != null) {
				CheckObr.error("Cannot set flags \"eager\" or \"hide\" on a dependency "
						+ dep.getIdentifier());
			}

			// Checking if the exception is existing
			String except = dep.getMissingException();
			if (except != null
					&& OBRGeneratorMojo.classpathDescriptor
					.getElementsHavingClass(except) == null) {
				error("Exception " + except + " undefined in " + dep);
			}

			//Checking if the interface is existing
			if (dep.getTarget() instanceof ResourceReference) {
				checkInterfaceExist(dep.getTarget().getName());
			}		

		}
	}


	/**
	 * Provided a dependency declaration, compute the effective dependency, adding group constraint and flags.
	 * Compute which is the good target, and check the targets are compatible. 
	 * If needed changes the target to set the more general one.
	 * @param depComponent
	 * @param dependency
	 * @return
	 */
	public static DependencyDeclaration computeGroupDependency (ComponentDeclaration depComponent, DependencyDeclaration dependency) {
		String depName = dependency.getIdentifier() ;

		//look for that dependency declaration above
		ComponentDeclaration group = ApamCapability.getDcl(depComponent.getGroupReference()) ;
		DependencyDeclaration groupDep = null ;
		while (group != null && (groupDep == null)) {
			groupDep = group.getDependency(depName) ;
			group = ApamCapability.getDcl(group.getGroupReference()) ;
		}

		if (groupDep == null) {
			//It is not defined above. Return it as is.
			return dependency ;
		}

		//it is declared above. Merge and check.
		//First merge flags, and then constraints.
		UtilComp.overrideDepFlags (dependency, groupDep, false);
		dependency.getImplementationConstraints().addAll(groupDep.getImplementationConstraints()) ;
		dependency.getInstanceConstraints().addAll(groupDep.getInstanceConstraints()) ;
		dependency.getImplementationPreferences().addAll(groupDep.getImplementationPreferences()) ;
		dependency.getInstancePreferences().addAll(groupDep.getInstancePreferences()) ;		

		//Check if the targets are compatible. Keep the most general one;
		if (dependency.getTarget() == null) {
			//set the target
			dependency.setTarget(groupDep.getTarget()) ;
			return dependency ;
		} 
		if (dependency.getTarget() instanceof ResourceReference) {
			//It is an interface or message. So either the same interface, or an interface of the group component
			if (groupDep.getTarget() instanceof ResourceReference) {
				//must be the same one
				if (groupDep.getTarget().equals(dependency.getTarget())) 
					return dependency ;
				CheckObr.error ("Invalid target for dependency " + dependency + " expected " + groupDep.getTarget()) ;
				return null ;
			}
			//group is against a component. Check if that component implements the resource.
			ComponentDeclaration groupTarget = ApamCapability.getDcl(((ComponentReference<?>)groupDep.getTarget())) ;
			if (groupTarget.getProvidedResources().contains((ResourceReference)dependency.getTarget())) {
				//Keep the component as target
				dependency.setTarget(groupDep.getTarget()) ;
				return dependency ;
			}
			CheckObr.error ("Invalid target for dependency " + dependency + " In component " + groupDep + " dependency " 
					+ depName + " is definned against " + groupDep.getTarget() + " which does not implement " + dependency.getTarget()) ;
			return null ;
		}

		//target is a component reference, group should be too
		if (!(groupDep.getTarget() instanceof ComponentReference<?>)) {
			CheckObr.error ("Invalid target for dependency " + dependency + " In component " + groupDep + " dependency " 
					+ depName + " is definned against " + groupDep.getTarget() ) ;
			return null ;
		}
		//Should be the same or "above". The group target is supposed to be above the current dependency target.

		ComponentDeclaration targetCompo = ApamCapability.getDcl(((ComponentReference<?>)dependency.getTarget())) ;
		while (targetCompo != null) {
			if (targetCompo.getReference().equals(groupDep.getTarget())) {
				dependency.setTarget(groupDep.getTarget()) ;
				return dependency ;							
			}
			targetCompo = ApamCapability.getDcl(targetCompo.getGroupReference()) ;
		}
		CheckObr.error ("Invalid target for dependency " + dependency + " In component " + groupDep + " dependency " 
				+ depName + " is definned against " + groupDep.getTarget() ) ;
		return null ;		
	}


	/**
	 * Provided a dependency "dep" (simple or complex) checks if the field type
	 * and attribute multiple are compatible. For complex dependency, for each
	 * field, checks if the target specification implements the field resource.
	 * 
	 * @param dep
	 *            : a dependency
	 * @param component
	 *            : the component currently analyzed
	 */
	private static void checkFieldTypeDep(DependencyDeclaration dep) {
		// All field must have same multiplicity, and must refer to interfaces
		// and messages provided by the specification.

		// In the case of implementation dependencies, we allow the field to be
		// of the main class of the implementation

		Set<ResourceReference> allowedTypes = new HashSet<ResourceReference>();

		// possible if only the dep ID is provided. Target will be computed later
		if (dep.getTarget() == null) return ;

		ComponentReference<?> targetComponent =  dep.getTarget().as(ComponentReference.class);

		// If not explicit component target, it must be an interface reference
		if (targetComponent == null) {
			allowedTypes.add(dep.getTarget().as(ResourceReference.class));
		}
		else {
			ApamCapability cap 	= ApamCapability.get(targetComponent);
			if (cap == null) {
				CheckObr.error("Dependency "
						+ dep.getIdentifier()
						+ " : the target of the reference doesn' exists "
						+ targetComponent);
			}

			allowedTypes.addAll(cap.getProvideResources());

			// check target's implementation class
			String implementationClass = cap.getImplementationClass();
			if (implementationClass != null)
				allowedTypes.add(new ImplementatioClassReference(implementationClass));
		}

		for (DependencyInjection innerDep : dep.getInjections()) {

			if (!innerDep.isValidInstrumentation())
				CheckObr.error(dep.getComponent().getName() + " : invalid type for field " + innerDep.getName());


			String type = innerDep.getResource().getJavaType();
			if (!(innerDep.getResource() instanceof UndefinedReference)
					&& !(allowedTypes.contains(innerDep.getResource()))) {
				CheckObr.error("Field "
						+ innerDep.getName()
						+ " is of type "
						+ type
						+ " which is not implemented by specification or implementation "
						+ dep.getTarget().getName());
			}
		}
	}

	/**
	 * This class represents the main class of an implementation.
	 * 
	 * TODO Right now this class is only used to verify the type of a field in an implementation dependnecy.
	 * We could think of generalizing the concept of "interface dependency" to "class dependency" and allow APAM to
	 * resolve a field of a given class to an instance of some implementation that implements this class. In that
	 * case we can move this class to the core of APAM.
	 */
	private static class ImplementatioClassReference extends ResourceReference {

		protected ImplementatioClassReference(String type) {
			super(type);
		}

		@Override
		public String toString() {
			return "class " + getIdentifier();
		}

	}

	/**
	 * Provided an atomic dependency, returns if it is multiple or not. Checks
	 * if the same field is declared twice.
	 * 
	 * @param dep
	 * @param component
	 * @return
	 */
	public static boolean isFieldMultiple(DependencyInjection dep,
			ComponentDeclaration component) {
		if (CheckObr.allFields.contains(dep.getName())
				&& !dep.getName().equals(CheckObr.UNDEFINED)) {
			CheckObr.error("In " + component.getName() + " field/method "
					+ dep.getName() + " allready declared");
		} else {
			CheckObr.allFields.add(dep.getName());
		}

		return dep.isCollection();
	}

	/**
	 * Checks if the component characteristics : shared, exclusive,
	 * instantiable, singleton, when explicitly defined, are not in
	 * contradiction with the group definition.
	 * 
	 * @param component
	 */
	public static void checkComponentHeader(ComponentDeclaration component) {
		ApamCapability cap = ApamCapability.get(component.getReference());
		if (cap == null)
			return;
		ApamCapability group = cap.getGroup();

		while (group != null) {
			if (cap.shared() != null && group.shared() != null
					&& (cap.shared() != group.shared())) {
				error("The \"shared\" property is incompatible with the value declared in "
						+ group.getName());
			}
			if (cap.instantiable() != null && group.instantiable() != null
					&& (cap.instantiable() != group.instantiable())) {
				error("The \"Instantiable\" property is incompatible with the value declared in "
						+ group.getName());
			}
			if (cap.singleton() != null && group.singleton() != null
					&& (cap.singleton() != group.singleton())) {
				error("The \"Singleton\" property is incompatible with the value declared in "
						+ group.getName());
			}
			group = group.getGroup();
		}
	}

	/**
	 * check all the characteristics that can be found in the <contentMngt> of a
	 * composite
	 * 
	 * @param component
	 */
	public static void checkCompositeContent(CompositeDeclaration component) {

		checkStart(component);
		checkState(component);
		checkOwn(component); // and grant
		checkVisibility(component);
		checkContextualDependencies(component);
		checkPromote(component);
	}

	/**
	 * Check the start characteristic. It is very similar to an instance
	 * declaration, plus a trigger.
	 * 
	 * @param component
	 */
	private static void checkStart(CompositeDeclaration component) {
		for (InstanceDeclaration start : component.getInstanceDeclarations()) {
			ImplementationReference<?> implRef = start.getImplementation();
			if (implRef == null) {
				error("Implementation name cannot be null");
				continue;
			}
			ApamCapability cap = ApamCapability.get(implRef);
			if (cap == null) {
				continue;
			}
			for (String attr : start.getProperties().keySet()) {
				String defAttr = getDefAttr(cap, attr, start.getProperties()
						.get(attr));
				if (defAttr == null
						|| Attribute.checkAttrType(attr,
								start.getProperties().get(attr), defAttr) == null) {
					error("invalid attribute " + attr + " = "
							+ start.getProperties().get(attr));
				}
			}

			checkDependencies(start);

			checkTrigger(start);
		}
	}

	private static boolean checkFilters(ComponentDeclaration component, Set<String> filters, List<String> listFilters, Map<String, String> validAttr,
			String comp) {
		boolean ok = true;
		if (filters != null) {
			for (String f : filters) {
				ApamFilter parsedFilter = ApamFilter.newInstance(f);
				if (parsedFilter == null || !checkFilter(parsedFilter, component, validAttr, f, comp)) {
					ok = false;
				}
			}
		}
		if (listFilters != null) {
			for (String f : listFilters) {
				ApamFilter parsedFilter = ApamFilter.newInstance(f);
				if (parsedFilter == null || !checkFilter(parsedFilter, component, validAttr, f, comp)) {
					ok = false;
				}
			}
		}
		return ok;
	}


	private static void checkTrigger(InstanceDeclaration start) {
		Set<ConstrainedReference> trig = start.getTriggers();
		for (ConstrainedReference ref : trig) {
			ResolvableReference target = ref.getTarget();
			ComponentReference<?> compoRef = target.as(ComponentReference.class);
			if (compoRef == null) {
				error("Start trigger not related to a valid component");
				continue;
			}
			ApamCapability cap = ApamCapability.get(compoRef);
			if (cap == null) {
				// error ("Unknown component " + target.getName()) ;
				continue;
			}

			Map<String, String> validAttrs = cap.getValidAttrNames();

			checkFilters(start, ref.getImplementationConstraints(), ref
					.getImplementationPreferences(), validAttrs, ref
					.getTarget().getName());
			checkFilters(start, ref.getInstanceConstraints(), ref
					.getInstancePreferences(), validAttrs, ref.getTarget()
					.getName());
		}
	}

	/**
	 * Check the state characteristic. <own specification="Door"
	 * property="location" value="{entrance, exit}">
	 * 
	 * @param component
	 */
	private static Set<String> checkState(CompositeDeclaration component) {
		PropertyDefinition.Reference ref = component.getStateProperty();
		if (ref == null) {
			return null;
		}

		ComponentReference<?> compo = ref.getDeclaringComponent();
		if (!(compo instanceof ImplementationReference)) {
			error("A state must be associated with an implementation.");
			return null;
		}
		ApamCapability implCap = ApamCapability.get(compo);
		if (implCap == null) {
			error("Implementation for state unavailable: " + compo.getName());
			return null;
		}
		// Attribute state must be defined on the implementation.
		String type = implCap.getLocalAttrDefinition(ref.getIdentifier());
		if (type == null) {
			error("The state attribute " + ref.getIdentifier()
					+ " on implementation " + compo.getName()
					+ " is undefined.");
			return null;
		}

		Set<String> values = Util.splitSet(type);
		if (values.isEmpty()) {
			error("State attribute " + ref.getIdentifier()
					+ " is not an enumeration. Invalid state attribute");
			return null;
		}
		return values;
	}

	private static boolean visibilityExpression(String expr) {
		if (expr == null)
			return true;

		if (expr.equals(CST.V_FALSE) || expr.equals(CST.V_TRUE))
			return true;

		return checkSyntaxFilter(expr);
	}

	private static void checkVisibility(CompositeDeclaration component) {
		VisibilityDeclaration visiDcl = component.getVisibility();
		if (!visibilityExpression(visiDcl.getApplicationInstances()))
			error("bad expression in ExportApp visibility: "
					+ visiDcl.getApplicationInstances());
		if (!visibilityExpression(visiDcl.getExportImplementations()))
			error("bad expression in Export implementation visibility: "
					+ visiDcl.getExportImplementations());
		if (!visibilityExpression(visiDcl.getExportInstances()))
			error("bad expression in Export instance visibility: "
					+ visiDcl.getExportInstances());
		if (!visibilityExpression(visiDcl.getImportImplementations()))
			error("bad expression in Imports implementation visibility: "
					+ visiDcl.getImportImplementations());
		if (!visibilityExpression(visiDcl.getImportInstances()))
			error("bad expression in Imports instance visibility: "
					+ visiDcl.getImportInstances());
	}

	/**
	 * 
	 * @param component
	 */
	private static void checkOwn(CompositeDeclaration component) {
		Set<OwnedComponentDeclaration> owned = component.getOwnedComponents();

		if (owned.isEmpty())
			return;

		// The composite must be a singleton
		if (!component.isSingleton()) {
			CheckObr.error("To define \"own\" clauses, composite "
					+ component.getName() + " must be a singleton.");
		}
		// check that a single own clause is defined for a component and its
		// members
		Set<String> compRef = new HashSet<String>();
		for (OwnedComponentDeclaration own : owned) {
			ApamCapability ownCap = ApamCapability.get(own.getComponent());
			if (ownCap == null) {
				error("Unknown component in own expression : "
						+ own.getComponent().getName());
				continue;
			}
			
			ComponentReference<?> foundReference = ApamCapability.getDcl(own.getComponent()).getReference();
			if (! own.getComponent().getClass().isAssignableFrom(foundReference.getClass()) ) {
				error("Component in own expression is of the wrong type, expecting "
						+ own.getComponent() +" found "+foundReference);
				continue;
				
			}

			// computes the attributes that can be associated with this spec or
			// implementations members
			if (own.getProperty() == null) {
				error("Need a property for an own clause");
				continue;
			}
			String prop = own.getProperty().getIdentifier();
			String type = ownCap.getAttrDefinition(prop);
			if (type == null) {
				error("Undefined attribute "
						+ own.getProperty().getIdentifier() + " for component "
						+ own.getComponent().getName() + " in own expression");
				continue;
			}
			Set<String> values = Util.splitSet(type);
			if (values.size() == 1) {
				error("Attribute " + own.getProperty().getIdentifier()
						+ " for component " + own.getComponent().getName()
						+ " is not an enumeration. Invalid in own expression");
				continue;
			}

			if (!values.containsAll(own.getValues())) {
				error("In own clause, invalid values for attribute " + prop
						+ "=" + Util.toStringResources(own.getValues())
						+ " \n    for component "
						+ own.getComponent().getName() + ". Expected "
						+ Util.toStringResources(values));
			}

			/**
			 * Check that a single own clause applies for the same component,
			 * and members At execution must also be checked that if other grant
			 * clauses in other composites for that component or its members:
			 * -It must be the same property -It must be different values
			 */
			if (compRef.contains(own.getComponent().getName())) {
				error("Another Own clause exists for "
						+ own.getComponent().getName()
						+ " in this composite declaration");
				continue;
			}
			compRef.add(own.getComponent().getName());
			if (ownCap.getGroup() != null) {
				compRef.add(ownCap.getGroup().getName());
			}

			checkGrant(component, own);
		}
	}

	private static void checkGrant(CompositeDeclaration component,
			OwnedComponentDeclaration own) {
		// Get state definition
		Set<String> stateDefinition = checkState(component);
		if (stateDefinition == null || stateDefinition.isEmpty()) { // No valid
			// state declaration.
			// No valid grants.
			return;
		}

		// List<GrantDeclaration> grants = own.getGrants();
		for (GrantDeclaration grant : own.getGrants()) {
			ApamCapability grantComponent = ApamCapability.get(grant
					.getDependency().getDeclaringComponent());
			ComponentDeclaration ownedComp = ApamCapability.getDcl(own
					.getComponent());

			// Check that the granted component exists
			if (grantComponent == null) {
				error("Unknown component "
						+ grant.getDependency().getDeclaringComponent()
						.getName() + " in grant expression : " + grant);
				continue;
			}

			// Check that the component is a singleton
			if (!CST.SINGLETON
					.equals(grantComponent.getProperty(CST.SINGLETON))) {
				CheckObr.warning("In Grant clause, Component "
						+ grantComponent.getName() + " is not a singleton");
			}

			// Check that grant state values are valid
			Set<String> grantStates = grant.getStates();
			if (!stateDefinition.containsAll(grant.getStates())) {
				error("In Grant expression, invalid values "
						+ Util.toStringResources(grant.getStates())
						+ " for state="
						+ Util.toStringResources(stateDefinition));
			}

			// Check that a single grant for a given state.
			for (String def : grantStates) {
				if (allGrants.contains(def)) {
					error("Component " + own.getComponent().getName()
							+ " already granted when state is " + def);
					continue;
				}
				allGrants.add(def);
			}

			// Check that the dependency exists and has as target the OWN
			// resource
			ComponentDeclaration granted = grantComponent.dcl;
			String id = grant.getDependency().getIdentifier();
			ComponentReference<?> owned = own.getComponent();
			boolean found = false;
			// OWN is a specification or an implem
			// granted dep can be anything
			for (DependencyDeclaration depend : granted.getDependencies()) {
				if (depend.getIdentifier().equals(id)) {
					found = true;

					// Check if the dependency leads to the OWNed component
					if (depend.getTarget().getClass().equals(owned.getClass()) 
							/* same type spec or implem	 */
							&& (depend.getTarget().getName().equals(owned
									.getName()))) {
						break;
					}

					// If the dependency is an implem check if its spec is the
					// owned one
					if (depend.getTarget() instanceof ImplementationReference) {
						ApamCapability depSpec = ApamCapability
								.get((ImplementationReference<?>) depend
										.getTarget());
						if (depSpec != null
								&& depSpec.getGroup().equals(owned.getName())) {
							break;
						}
					}

					// Check if the dependency resource are provided by the
					// owned component
					if ((depend.getTarget() instanceof ResourceReference)
							&& (ownedComp.getProvidedResources()
									.contains(depend.getTarget()))) {
						break;
					}

					// This id does not lead to the owned component
					CheckObr.error("The dependency of the grant clause "
							+ grant
							+ " does not refers to the owned component "
							+ owned);
				}
			}
			if (!found) {
				CheckObr.error("The dependency id of the grant clause "
						+ grant
						+ " is undefined for component "
						+ grant.getDependency().getDeclaringComponent()
						.getName());
			}
		}
	}

	/**
	 * Cannot check almost nothing ! Because of wild cards, components are not
	 * known, and their attribute and dependencies cannot be checked. Only the
	 * syntax of filters can be checked. the exceptions
	 * 
	 * @param component
	 */
	private static void checkContextualDependencies(
			CompositeDeclaration component) {
		for (DependencyDeclaration pol : component.getContextualDependencies()) {

			for (String constraint : pol.getImplementationConstraints()) {
				checkSyntaxFilter(constraint);
			}
			for (String constraint : pol.getImplementationPreferences()) {
				checkSyntaxFilter(constraint);
			}
			for (String constraint : pol.getInstanceConstraints()) {
				checkSyntaxFilter(constraint);
			}
			for (String constraint : pol.getInstancePreferences()) {
				checkSyntaxFilter(constraint);
			}

			// Checking if the exception is existing
			String except = pol.getMissingException();
			// for (String cp :
			// (Set<ClasspathDescriptor>)OBRGeneratorMojo.classpathDescriptor.getClasss())
			// {
			// System.out.println(cp);
			// }
			if (except != null
					&& OBRGeneratorMojo.classpathDescriptor
					.getElementsHavingClass(except) == null) {
				error("Exception " + except + " undefined in " + pol);
			}

		}
	}

	/**
	 * Cannot check if the component dependency is valid. Only checks that the
	 * composite dependency is declared, and that the component is known.
	 * 
	 * @param composite
	 */
	private static void checkPromote(CompositeDeclaration composite) {
		if (composite.getPromotions() == null)
			return;

		for (DependencyPromotion promo : composite.getPromotions()) {
			ComponentDeclaration internalComp = ApamCapability.getDcl(promo
					.getContentDependency().getDeclaringComponent());
			if (internalComp == null) {
				error("Invalid promotion: unknown component "
						+ promo.getContentDependency().getDeclaringComponent()
						.getName());
			}
			DependencyDeclaration internalDep = null;
			if (internalComp.getDependencies() != null)
				for (DependencyDeclaration intDep : internalComp
						.getDependencies()) {
					if (intDep.getIdentifier().equals(
							promo.getContentDependency().getIdentifier())) {
						internalDep = intDep;
						break;
					}
				}
			// Check if the dependencies are compatible
			if (internalDep == null) {
				error("Component " + internalComp.getName()
						+ " does not define a dependency with id="
						+ promo.getContentDependency().getIdentifier());
				continue;
			}

			DependencyDeclaration compositeDep = null;
			if (composite.getDependencies() != null)
				for (DependencyDeclaration dep : composite.getDependencies()) {
					if (dep.getIdentifier().equals(
							promo.getCompositeDependency().getIdentifier())) {
						compositeDep = dep;
						break;
					}
				}
			if (compositeDep == null) {
				error("Undefined composite dependency: "
						+ promo.getCompositeDependency().getIdentifier());
				continue;
			}

			// Both the composite and the component have a dependency with the
			// right id.
			// Check if the targets are compatible
			if (!checkDependencyMatch(internalDep, compositeDep)) {
				error("Dependency " + internalDep
						+ " does not match the composite dependency "
						+ compositeDep);
			}
		}
	}

	// Copy paste of the Util class ! too bad, this one uses ApamCapability
	private static boolean checkDependencyMatch(
			DependencyDeclaration clientDep, DependencyDeclaration compoDep) {
		boolean multiple = clientDep.isMultiple();
		// Look for same dependency: the same specification, the same
		// implementation or same resource name
		// Constraints are not taken into account

		if (compoDep.getTarget().getClass()
				.equals(clientDep.getTarget().getClass())) { // same nature
			if (compoDep.getTarget().equals(clientDep.getTarget())) {
				if (!multiple || compoDep.isMultiple())
					return true;
			}
		}

		// Look for a compatible dependency.
		// Stop at the first dependency matching only based on same name (same
		// resource or same component)
		// No provision for : cardinality, constraints or characteristics
		// (missing, eager)
		// for (DependencyDeclaration compoDep : compoDeps) {
		// Look if the client requires one of the resources provided by the
		// specification
		if (compoDep.getTarget() instanceof SpecificationReference) {
			SpecificationDeclaration spec = (SpecificationDeclaration) ApamCapability
					.getDcl(((SpecificationReference) compoDep.getTarget()));

			if ((spec != null)
					&& spec.getProvidedResources().contains(
							clientDep.getTarget()))
				if (!multiple || compoDep.isMultiple())
					return true;
		} else {
			// If the composite has a dependency toward an implementation
			// and the client requires a resource provided by that
			// implementation
			if (compoDep.getTarget() instanceof ImplementationReference) {
				//				String implName = ((ImplementationReference<?>) compoDep
				//						.getTarget()).getName();
				ImplementationDeclaration impl = (ImplementationDeclaration) ApamCapability
						.getDcl(((ImplementationReference<?>) compoDep.getTarget()));
				if (impl != null) {
					// The client requires the specification implemented by that
					// implementation
					if (clientDep.getTarget() instanceof SpecificationReference) {
						String clientReqSpec = ((SpecificationReference) clientDep
								.getTarget()).getName();
						if (impl.getSpecification().getName()
								.equals(clientReqSpec))
							if (!multiple || compoDep.isMultiple())
								return true;
					} else {
						// The client requires a resource provided by that
						// implementation
						if (impl.getProvidedResources().contains(
								clientDep.getTarget()))
							if (!multiple || compoDep.isMultiple())
								return true;
					}
				}
			}
		}
		return false;

	}
}
