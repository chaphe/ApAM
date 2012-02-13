package fr.imag.adele.apam.apamMavenPlugin;

import java.io.File;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.osgi.impl.bundle.obr.resource.RepositoryImpl;
import org.osgi.service.obr.Capability;
import org.osgi.service.obr.Resource;

import fr.imag.adele.apam.apamImpl.CST;
import fr.imag.adele.apam.util.ApamFilter;
import fr.imag.adele.apam.util.OBR;
import fr.imag.adele.apam.util.Util;
import fr.imag.adele.apam.core.*;
import fr.imag.adele.apam.core.ResourceReference.ResourceType;

public class CheckObr {

    private static RepositoryImpl repo;
    private static Resource[]     resources;

    private static final Map<String, Capability> readSpecs        = new HashMap<String, Capability>();
    private static final Set<String>             allFields         = new HashSet<String>();

    public static void init(String defaultOBRRepo) {
        File theRepo = new File(defaultOBRRepo);
        try {
            CheckObr.repo = new RepositoryImpl(theRepo.toURI().toURL());
            CheckObr.repo.refresh();
            // System.out.println("read repo " + defaultOBRRepo);
            CheckObr.resources = CheckObr.repo.getResources();

        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Checks that list in spec, is contained in Impl.
     * both impl and spec are under the form "{A, B, .... }"
     * 
     * @param set
     * @param subSet
     * @param msg
     */
    private static void checkList(String set, String subSet, String msg) {
        if (subSet == null)
            return;
        List<String> implList = Util.splitList(set);
        // each element of sp must be found in implList
        for (String sp : Util.split(subSet)) {
            if (!implList.contains(sp)) {
                System.err.println(msg + sp + ". Declared: " + set);
            }
        }
    }

    /**
     * 
     * @param dep
     */
    public static void checkConstraints(DependencyDeclaration dep) {
        if ((dep == null) || (dep.getResource().resourceType != ResourceType.SPECIFICATION))
            return;
        String spec = dep.getResource().getName();

        //        return;
        //        String spec = null;
        //        switch (dep.getResource().resourceType) {
        //            case COMPLEX:
        //                spec = ((DependencyDeclaration.ImplementationDeclaration) dep).specification;
        //                break;
        //            case IMPLEMENTATION:
        //                AtomicDependencyDeclaration adep = (AtomicDependencyDeclaration) ((DependencyDeclaration.ImplementationDependencyDeclaration) dep).dependencies
        //                .toArray()[0];
        //                if (adep.targetKind == TargetKind.SPECIFICATION) 
        //                    spec = adep.fieldName ;
        //                break ;
        //            case COMPOSITE :
        //                if (((Dependency.CompositeDependency) dep).targetKind == TargetKind.SPECIFICATION)
        //                    spec = ((Dependency.CompositeDependency) dep).fieldType;
        //                break;
        //            case SPECIFICATION:
        //                if (((DependencyDeclaration.SpecificationDependencyDeclaration) dep).targetKind == TargetKind.SPECIFICATION)
        //                    spec = ((DependencyDeclaration.SpecificationDependencyDeclaration) dep).fieldType;
        //                break ;
        //        }
        //        if (spec == null)
        //            return;
        Capability cap = CheckObr.getSpecCapability(spec);
        if (cap == null)
            return;
        Set<String> validImplAttrs = CheckObr.getValidImplAttributes(cap);
        //        Set<String> validInstAttrs = CheckObr.getValidInstAttributes(cap);

        CheckObr.checkFilterSet(dep.getImplementationConstraints(), validImplAttrs, spec);
        CheckObr.checkFilterList(dep.getImplementationPreferences(), validImplAttrs, spec);
        CheckObr.checkInstFilterSet(dep.getInstanceConstraints(), validImplAttrs, spec);
        CheckObr.checkInstFilterList(dep.getInstancePreferences(), validImplAttrs, spec);
    }

    /**
     * In theory we cannot check a constraint on an instance attributes since we do not know the implementation that
     * will be selected,
     * however, if the constraints contains "impl-name = xyz" we could do it.
     * 
     */
    public static void checkInstFilterList(List<String> filters, Set<String> validAttr, String spec) {
        if ((validAttr == null) || (filters == null))
            return;

        // try to see if implementation name "impl-name" is in the constraints
        String impl = null;
        for (String f : filters) {
            ApamFilter parsedFilter = ApamFilter.newInstance(f);
            // System.err.println("validating filter " + f);
            impl = parsedFilter.lookForAttr(CST.A_IMPLNAME);
            if (impl != null)
                break ;
        }
        // if implementation is found
        Capability cap = null;
        if (impl != null) {
            cap = CheckObr.getImplCapability(impl);
            if (cap != null) {
                // Map<String, Object> props = cap.getProperties();
                for (Object attr : cap.getProperties().keySet()) {
                    if (!((String) attr).startsWith(OBR.A_DEFINITION_PREFIX))
                        validAttr.add((String) attr);
                }
            }
        }
        if (cap != null)
            CheckObr.checkFilterList(filters, validAttr, impl);
        else
            CheckObr.checkFilterList(filters, validAttr, spec);
    }

    public static void checkInstFilterSet(Set<String> filters, Set<String> validAttr, String spec) {
        List<String> filterSet = new ArrayList<String>(filters);
        CheckObr.checkInstFilterList(filterSet, validAttr, spec);
    }

    public static void checkFilterList(List<String> filters, Set<String> validAttr, String spec) {
        for (String f : filters) {
            ApamFilter parsedFilter = ApamFilter.newInstance(f);
            // System.err.println("validating filter " + f);
            parsedFilter.validateAttr(validAttr, f, spec);
        }
    }

    public static void checkFilterSet(Set<String> filters, Set<String> validAttr, String spec) {
        for (String f : filters) {
            ApamFilter parsedFilter = ApamFilter.newInstance(f);
            // System.err.println("validating filter " + f);
            parsedFilter.validateAttr(validAttr, f, spec);
        }
    }

    /**
     * returns all the attributes that can be associated with an implementation:
     * attribute instantiated on the spec, plus those defined.
     * 
     * @param cap : the capability of the associated specification.
     * @return
     */
    private static Set<String> getValidImplAttributes(Capability cap) {
        if (cap == null)
            return null;
        Set<String> validAttrs = new HashSet<String>();
        String attr;
        for (Object attrObject : cap.getProperties().keySet()) {
            attr = (String) attrObject;
            if (attr.startsWith(OBR.A_DEFINITION_PREFIX))
                validAttrs.add(attr.substring(11));
            else
                validAttrs.add(attr);
        }
        return validAttrs;
    }

    //        private static Set<String> getValidInstAttributes(Capability cap) {
    //            if (cap == null)
    //                return null;
    //            Set<String> validAttrs = new HashSet<String>();
    //            // for (String predef : CheckObr.predefAttributes) {
    //            // validAttrs.add(predef);
    //            // }
    //            String attr;
    //    
    //            return validAttrs;
    //            xxx
    //        }

    /**
     * Check if attribute "attr" is defined in the list of attributes and definitions found in props
     * Props contains attribute (Cannot be redefined), and attribute definitions.
     * All predefined attributes are Ok (scope ...)
     * Cannot be a reserved attribute
     */
    private static boolean capContainsDefAttr(Map<String, Object> props, String attr) {
        if (Util.isPredefinedAttribute(attr))
            return true;

        if (Util.isReservedAttribute(attr)) {
            System.err.println("ERROR: " + attr + " is a reserved attribute");
            return false;
        }

        for (Object prop : props.keySet()) {
            if (((String) prop).equalsIgnoreCase(attr)) {
                System.err.println("ERROR: cannot redefine attribute " + attr);
                return false;
            }
        }
        attr = OBR.A_DEFINITION_PREFIX + attr;
        for (Object prop : props.keySet()) {
            if (((String) prop).equals(attr)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check the consistency of an instance :
     * -Existence of its implementation,
     * -Validity of its attributes,
     * -Validity of its constraints.
     * 
     * @param instance
     */
    public static void checkInstance(InstanceDeclaration instance) {
        ImplementationReference impl = instance.getImplementation();
        String name = instance.getName();
        if (impl == null) {
            System.err.println("ERROR: implementation name missing for instance " + name);
            return;
        }
        CheckObr.checkInstAttributes(impl.getName(), name, instance);
        Set<DependencyDeclaration> deps = instance.getDependencies();
        if (deps == null)
            return;
        for (DependencyDeclaration dep : deps) {
            CheckObr.checkConstraints(dep);
        }
    }

    /**
     * 
     * @param implName
     * @param spec
     * @param properties
     */
    public static void checkImplAttributes(ImplementationDeclaration component) {
        String implName = component.getName();
        String spec = component.getSpecification().getName();
        Map<String, Object> properties = component.getProperties();
        if (spec == null)
            return;
        Capability cap = CheckObr.getSpecCapability(spec);
        if (cap == null) {
            return;
        }
        // each attribute in properties must be declared in spec.
        Map<String, Object> props = cap.getProperties();
        for (String attr : properties.keySet()) {
            if (!CheckObr.capContainsDefAttr(props, attr)) {
                System.err.println("In implementation " + implName + ", attribute " + attr
                        + " used but not defined in "
                        + spec);
            }
        }
    }

    /**
     * Provided component is an instance "name", and impl its implem in cap, check if the instance attribute are valid.
     * 
     * @param impl
     * @param name
     * @param cap
     * @param instance
     */
    public static void checkInstAttributes(String impl, String name, InstanceDeclaration instance) {
        Capability capImpl = CheckObr.getImplCapability(impl);
        if (capImpl == null) {
            return;
        }

        Map<String, Object> properties = instance.getProperties();

        Map<String, Object> props = capImpl.getProperties();

        // Add spec attributes
        // each attribute in properties must be declared in cap.
        for (String attr : properties.keySet()) {
            if (!CheckObr.capContainsDefAttr(props, attr)) {
                if (name == null) {
                    System.err.println("In instance, attribute " + attr
                            + " used but not defined in " + impl);
                } else {
                    System.err.println("In instance " + name + ", attribute " + attr
                            + " used but not defined in " + impl);
                }
            }
        }
    }

    /**
     * An implementation has the following provide; check if consistent with its specification.
     * First tries to find the spec in obr; if found check that all the spec provide are included
     * 
     * @param spec. Can be null.
     * @param interfaces = "{I1, I2, I3}" or I1 or null
     * @param messages= "{M1, M2, M3}" or M1 or null
     * @return
     */
    public static boolean checkImplProvide(String implName, String spec, String interfaces, String messages) {
        if (spec == null)
            return true;
        Capability cap = CheckObr.getSpecCapability(spec);
        if (cap == null) {
            return true;
        }
        // CheckObr.printCap(cap);
        CheckObr.checkList(messages, CheckObr.getAttributeInCap(cap, OBR.A_PROVIDE_MESSAGES),
                "Implementation " + implName + " must produce message ");
        CheckObr.checkList(interfaces, CheckObr.getAttributeInCap(cap, OBR.A_PROVIDE_INTERFACES),
                "Implementation " + implName + " must implement interface ");

        return true;
    }


    public static void checkCompoMain(CompositeDeclaration composite) {
        String name = composite.getName();
        // System.err.println("in checkCompoMain ");
        String implName = composite.getMainImplementation().getName();
        Capability cap = CheckObr.getImplCapability(composite.getMainImplementation().getName());
        if (cap == null)
            return;
        String interfaces = composite.getProvidedRessourceString(ResourceType.INTERFACE);
        String messages = composite.getProvidedRessourceString(ResourceType.MESSAGE);
        String spec = composite.getProvidedRessourceString(ResourceType.SPECIFICATION);
        if (spec == null)
            return;
        String capSpec = CheckObr.getAttributeInCap(cap, OBR.A_PROVIDE_SPECIFICATION);
        if ((capSpec != null) && !spec.equals(capSpec)) {
            System.err.println("In " + name + " Invalid main implementation. " + implName
                    + " must implement specification " + spec);
        }
        CheckObr.checkList(CheckObr.getAttributeInCap(cap, OBR.A_PROVIDE_MESSAGES), messages,
                "In " + name + " Invalid main implementation. " + implName + " must produce message ");
        CheckObr.checkList(CheckObr.getAttributeInCap(cap, OBR.A_PROVIDE_INTERFACES), interfaces,
                "In " + name + " Invalid main implementation. " + implName + " must implement interface ");
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
        for (DependencyDeclaration dep : deps) {
            // validating dependency constraints and preferences..
            CheckObr.checkConstraints(dep);
            // Checking fields and complex dependencies
            CheckObr.checkFieldTypeDep(dep, component);
        }
    }


    public static void printRes(Resource aResource) {
        System.out.println("\n\nRessource SymbolicName : " + aResource.getSymbolicName() + " id: " + aResource.getId());
        for (Capability aCap : aResource.getCapabilities()) {
            CheckObr.printCap(aCap);
        }
    }

    public static void printCap(Capability aCap) {
        System.out.println("   Capability name: " + aCap.getName());
        String value;
        for (Object prop : aCap.getProperties().keySet()) {
            Object val = aCap.getProperties().get(prop);
            System.out.println("type de value: " + val.getClass());
            System.out.println("     " + (String) prop + " val= " + aCap.getProperties().get(prop));
        }
    }

    private static String getAttributeInCap(Capability cap, String name) {
        if (cap == null)
            return null;
        Map<String, Object> props = cap.getProperties();
        List<String> prop = (List<String>) props.get(name);
        if (prop == null)
            return null;
        return (String) prop.toArray()[0];
    }

    private static Capability getSpecCapability(String name) {
        if (CheckObr.readSpecs.containsKey(name))
            return CheckObr.readSpecs.get(name);
        for (Resource res : CheckObr.resources) {
            if (OBRGeneratorMojo.bundleDependencies.contains(res.getId())) {
                for (Capability cap : res.getCapabilities()) {
                    if (cap.getName().equals(OBR.CAPABILITY_SPECIFICATION)
                            && (CheckObr.getAttributeInCap(cap, "name").equals(name))) {
                        System.out.println("Specification " + name + " found in bundle " + res.getId());
                        CheckObr.readSpecs.put(name, cap);
                        return cap;
                    }
                }
            }
        }
        System.out.println("Warning: Specification " + name + " not found in repository " + CheckObr.repo.getURL());
        return null;
    }

    private static Capability getImplCapability(String name) {
        if (CheckObr.readSpecs.containsKey(name))
            return CheckObr.readSpecs.get(name);
        for (Resource res : CheckObr.resources) {
            //            if (ApamMavenPlugin.bundleDependencies.contains(res.getId())) {
            for (Capability cap : res.getCapabilities()) {
                if (cap.getName().equals(OBR.CAPABILITY_IMPLEMENTATION)
                        && (CheckObr.getAttributeInCap(cap, "name").equals(name))) {
                    System.out.println("Implementation " + name + " found in bundle " + res.getId());
                    CheckObr.readSpecs.put(name, cap);
                    return cap;
                }
                //                }
            }
        }
        System.err.println("Implementation " + name + " not found in repository " + CheckObr.repo.getURL());
        return null;
    }

    private static Capability getCompoCapability(String name) {
        for (Resource res : CheckObr.resources) {
            for (Capability cap : res.getCapabilities()) {
                if (cap.getName().equals(OBR.CAPABILITY_IMPLEMENTATION)
                        && (CheckObr.getAttributeInCap(cap, CST.A_COMPOSITE) != null)
                        && (CheckObr.getAttributeInCap(cap, CST.A_COMPOSITE).equals("true"))
                        && (CheckObr.getAttributeInCap(cap, name) != null))
                    return cap;
            }
        }
        return null;
    }

    /**
     * Provided a dependency "dep" (simple or complex) checks if the field type and attribute multip^le are compatible.
     * For complex dependency, for each field, checks if the target specification implements the field resource.
     * 
     * @param dep : a dependency
     * @param component : the component currently analyzed
     */
    private static void checkFieldTypeDep(DependencyDeclaration dep, ComponentDeclaration component) {
        if (!(component instanceof AtomicImplementationDeclaration)) return ;

        // All field must have same multiplicity, and must refer to interfaces and messages provided by the specification.
        String interfs = CheckObr.getAttributeInCap(CheckObr.getSpecCapability(dep.getName()), OBR.A_PROVIDE_INTERFACES);
        List<String> specResources = Util.splitList(interfs);
        String messages = CheckObr.getAttributeInCap(CheckObr.getSpecCapability(dep.getName()), OBR.A_PROVIDE_MESSAGES);
        specResources.addAll( Util.splitList(messages));
        Boolean mult = dep.isMultiple();
        for (DependencyInjection innerDep : dep.getInjections()) {
            // check if attribute "multiple" matches the fields type (Set, List Array)
            // if multiple is not explicitly defined, assume the first field multiplicity
            if (mult == null) {
                dep.setMultiple(CheckObr.isFieldMultiple(innerDep, component));
                mult = dep.isMultiple();
            }
            if (mult != CheckObr.isFieldMultiple(innerDep, component)) {
                if (mult)
                    System.err.println("ERROR: in " + component.getName() + " field "
                            + innerDep.getResource().getName()
                            + " is a collection field, while other fields in same dependency are simple.");
                else
                    System.err.println("ERROR: in " + component.getName() + " field "
                            + innerDep.getResource().getName()
                            + " is a simple field, while other fields in same dependency are collection.");
            }
            if (!(specResources.contains(innerDep.getResource().getName()))) {
                System.err.println("ERROR: in " + component.getName() + " Field " + innerDep.getFieldName()
                        + " is of type "
                        + innerDep.getResource()
                        + " which is not implemented by specification " + dep.getName());
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
        if (CheckObr.allFields.contains(dep.getFieldName())) {
            System.err.println("ERROR: in " + component.getName() + " field " + dep.getFieldName()
                    + " allready declared");
        }
        else {
            CheckObr.allFields.add(dep.getFieldName());
        }
        //        private static final String[] fieldTypeMultiple = { "java.util.Set", "java.util.List",
        //            "java.util.Collection", "java.util.Vector" };

        String type = dep.getResource().getName();
        return (type.equals("java.util.Set") || type.equals("java.util.List") || type.endsWith("[]"));
    }
}
