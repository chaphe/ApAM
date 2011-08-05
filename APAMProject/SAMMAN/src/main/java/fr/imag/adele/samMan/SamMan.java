package fr.imag.adele.samMan;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.osgi.framework.Filter;

import fr.imag.adele.am.query.Query;
import fr.imag.adele.am.query.QueryLDAPImpl;
import fr.imag.adele.apam.CST;
import fr.imag.adele.apam.ManagerModel;
import fr.imag.adele.apam.apamAPI.ASMImpl;
import fr.imag.adele.apam.apamAPI.ASMInst;
import fr.imag.adele.apam.apamAPI.ASMSpec;
import fr.imag.adele.apam.apamAPI.Composite;
import fr.imag.adele.apam.apamAPI.Manager;
import fr.imag.adele.apam.apamAPI.ManagersMng;
import fr.imag.adele.apam.util.Util;
import fr.imag.adele.sam.Implementation;
import fr.imag.adele.sam.Instance;
import fr.imag.adele.sam.Specification;
import fr.imag.adele.sam.broker.ImplementationBroker;
import fr.imag.adele.sam.broker.InstanceBroker;

public class SamMan implements Manager {

    /*
     * The reference to APAM, injected by iPojo
     */
    private ManagersMng apam;

    @Override
    public String getName() {
        return "SAMMAN";
    }

    /**
     * SANMAN activated, register with APAM
     */
    public void start() {
        apam.addManager(this, 0);
    }

    public void stop() {
        apam.removeManager(this);
    }

    // TODO Must read the opportunist model and build the list of opportunist specs.
    // If empty, all is supposed to be opportunist ???
    private static Set<String> opportunismNames = new HashSet<String>();
    private static boolean     specOpportunist  = true;                 // if the model says all specifications are
    // opportunist
    private static boolean     implOpportunist  = true;                 // if the model says all implementations are

    // opportunist

    private boolean opportunistSpec(String specName) {
        if (SamMan.specOpportunist)
            return true;
        return true; // TODO waiting for the models
    }

    private boolean opportunistImpl(String implName) {
        if (SamMan.implOpportunist)
            return true;
        return SamMan.opportunismNames.contains(implName);
    }

    @Override
    public List<Manager> getSelectionPathSpec(ASMInst from, Composite composite, String interfaceName, String specName,
            Set<Filter> filter, List<Manager> involved) {
        if (opportunistSpec(specName)) {
            involved.add(this);
        }
        return involved;
    }

    @Override
    public List<Manager> getSelectionPathImpl(ASMInst from, Composite composite, String samImplName, String implName,
            Set<Filter> filter, List<Manager> involved) {
        if (opportunistImpl(implName))
            involved.add(this);

        return involved;
    }

    private Set<Instance> getSamInstanceInterf(String interfaceName) {
        Set<Instance> insts = new HashSet<Instance>();
        try {
            for (Instance instance : CST.SAMInstBroker.getInstances()) {
                Specification samSpec = instance.getSpecification();
                if (samSpec != null) {
                    String[] interfs = samSpec.getInterfaceNames();
                    for (String interf : interfs) {
                        if (interf.equals(interfaceName)) { // we found one
                            insts.add(instance);
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return insts;
    }

    @Override
    public ASMInst resolveSpec(Composite implComposite, Composite instComposite, String interfaceName, String specName,
            Set<Filter> constraints, List<Filter> preferences) {
        return resolveSpec0(implComposite, instComposite, interfaceName, specName, constraints, preferences, false,
                null);
    }

    @Override
    public Set<ASMInst> resolveSpecs(Composite implComposite, Composite instComposite, String interfaceName,
            String specName, Set<Filter> constraints, List<Filter> preferences) {
        Set<ASMInst> allInst = new HashSet<ASMInst>();
        resolveSpec0(implComposite, instComposite, interfaceName, specName, constraints, preferences, true, allInst);
        return allInst;
    }

    public ASMInst resolveSpec0(Composite implComposite, Composite instComposite, String interfaceName,
            String specName, Set<Filter> constraints, List<Filter> preferences, boolean multiple, Set<ASMInst> allInst) {
        if ((interfaceName == null) && (specName == null)) {
            System.err.println("ERROR : missing parameter interfaceName or specName");
            return null;
        }
        if (implComposite == null) {
            System.err.println("ERROR : missing parameter composite in resolveSpec");
            return null;
        }

        try {
            ASMSpec asmSpec;
            if (specName != null) {
                asmSpec = CST.ASMSpecBroker.getSpec(specName);
            } else {
                asmSpec = CST.ASMSpecBroker.getSpecInterf(interfaceName);
            }

            // Look by its specification
            Set<Instance> instInterf = new HashSet<Instance>();
            if (asmSpec != null) { // Look by its sam interface
                Specification spec = asmSpec.getSamSpec();
                if (spec != null) { // Is sam spec known ?
                    for (Instance inst : CST.SAMInstBroker.getInstances()) {
                        if (inst.getSpecification() == spec)
                            instInterf.add(inst);
                    }
                }
            } else { // Look by its interface
                if (interfaceName != null)
                    instInterf = getSamInstanceInterf(interfaceName);
            }
            // eliminate those instances that have an Apam impl. it has already been checked by ApamMan
            Set<Instance> allInstances = new HashSet<Instance>();
            for (Instance in : instInterf) {
                if (CST.ASMImplBroker.getImpl(in.getImplementation()) == null)
                    allInstances.add(in);
            }

            // check if it satisfies the constraints
            Set<Instance> matchInsts;
            if ((constraints == null) || constraints.isEmpty()) {
                matchInsts = allInstances;
            } else {
                matchInsts = new HashSet<Instance>();
                Filter filter = Util.buildFilter(constraints);
                Query query = new QueryLDAPImpl(filter.toString());
                for (Instance inst : allInstances) {
                    if (inst.match(query))
                        matchInsts.add(inst);
                }
            }

            ASMInst returnInst = null;
            for (Instance inst : matchInsts) {
                // ignore the Apam instances, they have been checked by ApamMan
                if (CST.ASMInstBroker.getInst(inst) == null) {
                    returnInst = CST.ASMInstBroker.addInst(implComposite, instComposite, inst, null, specName, null);
                    if (multiple)
                        allInst.add(returnInst);
                    else
                        return returnInst;
                }
            }

            // we have found a sam Instance.
            if (returnInst != null)
            	return null;
            
            // Last chance look for an implementation that implements the interface.
            if (interfaceName != null) {
                for (Implementation impl : CST.SAMImplBroker.getImplementations()) {
                    // if it is an Apam impl, it has already been checked by ApamMan
                    if (CST.ASMImplBroker.getImpl(impl) != null)
                        continue;
                    Specification samSpec = impl.getSpecification();
                    if (samSpec != null) {
                        for (String interf : samSpec.getInterfaceNames()) {
                            if (interf.equals(interfaceName)) { // We got an implementation
     
                                String apamImplName = (String)impl.getProperty(CST.PROPERTY_IMPLEMENTATION_NAME);
                                String apamSpecName = (String)impl.getProperty(CST.PROPERTY_COMPOSITE_MAIN_SPECIFICATION);

                            	// activate the implementation in APAM, an create a new instance by APAM API.
                            	// This will take care of the case of executable composites
                            	ASMImpl asmImpl = CST.ASMImplBroker.addImpl(implComposite, apamImplName, impl.getName(), apamSpecName, null);
                            	returnInst = asmImpl.createInst(instComposite, null); 
                            	
                               	if (multiple) {
                               		allInst.add(returnInst);}
                               	else
                               		return returnInst;
                               	
                               	return null;
                            	
                            }
                        }
                    }
                }
            }
            

        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    // the resolution from an Apam impl has been checked by ApamMan.
    // If ApamMan could nor resol, SamMan either.
    // Do nothing
    @Override
    public ASMInst resolveImpl(Composite implComposite, Composite instComposite, String samImplName, String implName,
            Set<Filter> constraints, List<Filter> preferences) {
        // return resolveImpl0(from, samImplName, implName, depName, constraints, false, null);
        return null;
    }

    // the resolution from an Apam impl has been checked by ApamMan.
    // If ApamMan could nor resol, SamMan either.
    // Do nothing
    @Override
    public Set<ASMInst> resolveImpls(Composite implComposite, Composite instComposite, String samImplName,
            String implName, Set<Filter> constraints, List<Filter> preferences) {
        Set<ASMInst> allInst = new HashSet<ASMInst>();
        // resolveImpl0(from, samImplName, implName, depName, constraints, true, allInst);
        return allInst;
    }

    @Override
    public int getPriority() {
        return 0;
    }

    @Override
    public void newComposite(ManagerModel model, Composite composite) {

    }

    @Override
    public List<Filter> getConstraintsSpec(String interfaceName, String specName, String depName,
            List<Filter> initConstraints) {
        return initConstraints;
    }

    @Override
    public ASMImpl resolveImplByName(Composite implComposite, Composite instComposite, String samImplName,
            String implName, Set<Filter> constraints, List<Filter> preferences) {
        return null;
    }

    @Override
    public ASMImpl resolveSpecByName(Composite implComposite, Composite instComposite, String interfaceName,
            String specName, Set<Filter> constraints, List<Filter> preferences) {
        return null;
    }

}
