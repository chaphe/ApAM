package fr.imag.adele.apam.impl;

import java.net.URL;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.osgi.framework.Filter;
import org.osgi.framework.InvalidSyntaxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.imag.adele.apam.CompositeType;
import fr.imag.adele.apam.Implementation;
import fr.imag.adele.apam.ImplementationBroker;
import fr.imag.adele.apam.Specification;
import fr.imag.adele.apam.apform.ApformImplementation;
import fr.imag.adele.apam.core.CompositeDeclaration;
import fr.imag.adele.apam.util.ApamInstall;

public class ImplementationBrokerImpl implements ImplementationBroker {

	private Logger logger = LoggerFactory.getLogger(ImplementationBrokerImpl.class);

    private final Set<Implementation> implems = Collections.newSetFromMap(new ConcurrentHashMap<Implementation, Boolean>());

   @Override
   public Implementation addImpl(CompositeType composite,ApformImplementation apfImpl, Map<String,Object> properties) {
    	
    	assert apfImpl != null;
    	assert getImpl(apfImpl.getDeclaration().getName()) == null;
    	
    	if (apfImpl == null) {
            logger.error("ERROR : missing apf Implementaion in addImpl");
            return null;
        }

        Implementation implementation = getImpl(apfImpl.getDeclaration().getName());
        if (implementation != null) { // do not create twice
//          if (specName != null)
//          ((SpecificationImpl) asmImpl.getSpec()).setName(specName);
            logger.error("Implementation already existing (in addImpl) " + implementation.getName());
            return implementation;
        }

        if (composite == null)
        	composite = CompositeTypeImpl.getRootCompositeType();
        
        // create a primitive or composite implementation
        if (apfImpl.getDeclaration() instanceof CompositeDeclaration) {
            implementation = new CompositeTypeImpl(composite,apfImpl,properties);
        } else {
            implementation = new ImplementationImpl(composite,apfImpl,properties);
        }

        ((ImplementationImpl)implementation).register();

        return implementation;
    }

    @Override
    public Implementation createImpl(CompositeType compo, String implName, URL url, Map<String, Object> properties) {

        assert implName != null && url != null;
        
        Implementation impl = getImpl(implName);
        if (impl != null) { // do not create twice
            return impl;
        }
        impl = ApamInstall.intallImplemFromURL(url,implName);
        if (impl == null) {
            logger.error("deployment failed :" + implName + " at URL " + url);
            return null;
        }

       if (compo != null && ! ((CompositeTypeImpl)compo).isSystemRoot())
        	((CompositeTypeImpl)compo).deploy(impl);
        
        return impl;
    }
    

    // Not in the interface. No control
    /**
     * TODO change visibility, currently this method is public to be visible from Apform
     */
    public void removeImpl(Implementation implementation) {
    	removeImpl(implementation,true);
    }
    
    protected void removeImpl(Implementation implementation, boolean notify) {
    	((ComponentImpl)implementation).unregister();
    }

    public void add(Implementation implementation) {
    	assert implementation != null && !implems.contains(implementation);
       	implems.add(implementation);
    }
    
    public void remove(Implementation implementation) {
    	assert implementation != null && implems.contains(implementation);
       	implems.remove(implementation);
    }

    @Override
    public Set<Implementation> getImpls() {
        return Collections.unmodifiableSet(implems);
        // return new HashSet<ASMImpl> (implems) ;
    }
    
    @Override
    public Implementation getImpl(String implName) {
        if (implName == null)
            return null;
        for (Implementation impl : implems) {
            if (implName.equals(impl.getName()))
                return impl;
        }
        return null;
    }

    @Override
    public Set<Implementation> getImpls(Filter goal) throws InvalidSyntaxException {
        if (goal == null)
            return getImpls();
        Set<Implementation> ret = new HashSet<Implementation>();
        for (Implementation impl : implems) {
            if (impl.match(goal))
                ret.add(impl);
        }
        return ret;
    }


    @Override
    public Implementation getImpl(ApformImplementation apfImpl) {
        String apfName = apfImpl.getDeclaration().getName();
        // Warning : for a composite main implem, both the composite type and the main implem refer to the same apf
        // implem
        for (Implementation implem : implems) {
            if ((implem.getApformImpl() == apfImpl) && implem.getName().equals(apfName)) {
                return implem;
            }
        }
        return null;
    }

    @Override
    public Set<Implementation> getImpls(Specification spec) {
        Set<Implementation> impls = new HashSet<Implementation>();
        for (Implementation impl : implems) {
            if (impl.getSpec() == spec)
                impls.add(impl);
        }
        return impls;
    }

}
