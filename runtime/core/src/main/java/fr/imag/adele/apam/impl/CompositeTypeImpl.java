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
package fr.imag.adele.apam.impl;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.imag.adele.apam.ApamManagers;
import fr.imag.adele.apam.CST;
import fr.imag.adele.apam.Component;
import fr.imag.adele.apam.Composite;
import fr.imag.adele.apam.CompositeType;
import fr.imag.adele.apam.DependencyManager;
import fr.imag.adele.apam.Implementation;
import fr.imag.adele.apam.ManagerModel;
import fr.imag.adele.apam.Specification;
import fr.imag.adele.apam.apform.ApformCompositeType;
import fr.imag.adele.apam.apform.ApformInstance;
import fr.imag.adele.apam.declarations.CompositeDeclaration;


public class CompositeTypeImpl extends ImplementationImpl implements CompositeType {

	
	private static Logger 		logger 				= LoggerFactory.getLogger(CompositeTypeImpl.class);
	private static final long 	serialVersionUID 	= 1L;
	
	/**
	 * The root of the composite type hierarchy
	 */
	private static CompositeType	rootCompoType;

	/**
	 * NOTE We can not directly initialize the field because the constructor may throw an exception, so we need to
	 * make a static block to be able to catch the exception. The root composite bootstraps the system, so normally
	 * we SHOULD always be able to create it; if there is an exception, that means there is some bug and we cannot
	 * continue so we throw a class initialization exception.
	 */
	static {
		CompositeType bootstrap = null;
		try {
			bootstrap 	= new CompositeTypeImpl();
		} catch (Exception e) {
			throw new ExceptionInInitializerError(e);
		}
		finally {
			rootCompoType = bootstrap;
		}
	}

    public static CompositeType getRootCompositeType() {
        return CompositeTypeImpl.rootCompoType;
    }
    
	/**
	 * The list of all composites in the APAM state model
	 */
    public static Collection<CompositeType> getRootCompositeTypes() {
        return CompositeTypeImpl.rootCompoType.getEmbedded();
    }
	
	private static Map<String, CompositeType> compositeTypes = new ConcurrentHashMap<String, CompositeType>();
    
    public static Collection<CompositeType> getCompositeTypes() {
        return Collections.unmodifiableCollection(CompositeTypeImpl.compositeTypes.values());
    }

    public static CompositeType getCompositeType(String name) {
        return CompositeTypeImpl.compositeTypes.get(name);
    }
    
    /*
     * The models associated to this composite type to specify the different strategies to handle the
     * instances of this type.
     */
    private Set<ManagerModel>	models		= new HashSet<ManagerModel>();

    /*
     * The contained implementations deployed (really or logically) by this composite type.
     * 
     * WARNING An implementation may be deployed by more than one composite type
     */
    private Set<Implementation>	contains	= Collections.newSetFromMap(new ConcurrentHashMap<Implementation, Boolean>());
    private Implementation		mainImpl	= null;

    /*
     *  The hierarchy of composite types. 
     *  
     *  This is a subset of the contains hierarchy restricted only to composites. 
     */
    private Set<CompositeType>	embedded	= Collections.newSetFromMap(new ConcurrentHashMap<CompositeType, Boolean>());
    private Set<CompositeType>	invEmbedded	= Collections.newSetFromMap(new ConcurrentHashMap<CompositeType, Boolean>());

    /*
     *  all the dependencies between composite types
     */
    private Set<CompositeType>	imports		= Collections.newSetFromMap(new ConcurrentHashMap<CompositeType, Boolean>());
    private Set<CompositeType>	invImports	= Collections.newSetFromMap(new ConcurrentHashMap<CompositeType, Boolean>());



    /**
     * This is an special constructor only used for the root type of the system 
     */
    private CompositeTypeImpl() throws InvalidConfiguration {
    	super(CST.ROOT_COMPOSITE_TYPE);
        
        /*
         * Look for platform models in directory "load" 
         */
        this.models = new HashSet<ManagerModel>();
        File modelDirectory = new File("conf");
        
        if (! modelDirectory.exists())
        	return;
        
        if (! modelDirectory.isDirectory())
        	return;
        
        for (File modelFile : modelDirectory.listFiles()) {
 			try {
 				String modelFileName = modelFile.getName();
 				
 				if (! modelFileName.endsWith(".cfg"))
 					continue;
 				
 				if (! modelFileName.startsWith(CST.ROOT_COMPOSITE_TYPE))
 					continue;
 				
	            String managerName = modelFileName.substring(CST.ROOT_COMPOSITE_TYPE.length()+1, modelFileName.lastIndexOf(".cfg"));
				URL modelURL = modelFile.toURI().toURL();
	            models.add(new ManagerModel(managerName, modelURL));
	            
			} catch (MalformedURLException e) {
			}			
		}
    }

    /**
     * Whether this is the system root composite type
     */
    public boolean isSystemRoot() {
    	return this == rootCompoType;
    }
    
    /**
     * Builds a new Apam composite type to represent the specified implementation in the Apam model.
     */
    protected CompositeTypeImpl(CompositeType composite, ApformCompositeType apfCompo) throws InvalidConfiguration {
        
    	super(composite,apfCompo);
  
		/*
		 * Reference the enclosing composite hierarchy
		 */
    	addInvEmbedded(composite);
     	
    	/*
    	 * Get declared models
    	 */
       	this.models.addAll(apfCompo.getModels());
    }

    @Override
    public void register(Map<String, String> initialProperties) throws InvalidConfiguration {
    	
    	/*
    	 * Opposite references from the enclosing composite types
    	 */
		for (CompositeType inComposite : invEmbedded) {
	        ((CompositeTypeImpl)inComposite).addEmbedded(this);
		}

    	/*
    	 * Notify managers of their models.
    	 * 
    	 * WARNING Notice that the managers are not notified at the end of the registration, but 
    	 * before resolving the main implementation. This allow the resolution of the main
    	 * implementation in the context of the composite, specially if the main implementation
    	 * is deployed in the private repository of the composite..
    	 * 
    	 * Managers must be aware that the composite type is not completely registered, so they
    	 * must be cautious when manipulating the state and navigating the hierarchy.
    	 */
        for (ManagerModel managerModel : models) {
        	DependencyManager manager = ApamManagers.getManager(managerModel.getManagerName());
            if (manager != null) {
            	manager.newComposite(managerModel, this);
            }
        }
		
        /*
         * Resolve main implementation. Does nothing if it is an abstract composite.
         */
        resolveMainImplem () ;
        
        /*
		 * add to list of composite types
		 */
		CompositeTypeImpl.compositeTypes.put(getName(),this);
		
		/*
		 * Complete normal registration
		 */		
    	super.register(initialProperties); 	
    }        
    
    /*
     * Resolve main implementation.
     * 
     * First we try to find an implementation with the name of the main component, if we fail to find one we
     * assume the name corresponds to a specification which is resolved. 
     * Notice that resolution of the main component is done in the context of this composite type, 
     * so it will be  deployed in this context if necessary.
     * 
     * WARNING this is done after the composite type is added to the hierarchy but before it is completely
     * registered as a normal implementation. We do not call super.register until the main implementation
     * is resolved.
     * 
     */
    private void resolveMainImplem () throws InvalidConfiguration {        
    	/*
    	 * Abstract Composite
    	 */
    	if (getCompoDeclaration().getMainComponent() == null) {
    		if (!getAllProvidedResources().isEmpty()) {
            	unregister();
            	throw new InvalidConfiguration("invalid composite type " 
                        + getName() + ". No Main implementation but provides resources " + getAllProvidedResources());
    		}
    		return ;
    	}
    	
		String mainComponent = getCompoDeclaration().getMainComponent().getName();
        /*
         * This is a false composite / instance, not registered anywhere. 
         * just to provide an instance to the find and resolve.
         */
        Composite dummyComposite = new CompositeImpl (this, "dummyComposite") ;
                
		
		Component mComponent = CST.apamResolver.findComponentByName(dummyComposite, mainComponent);
		if (mComponent!=null && mComponent instanceof Implementation){
		    logger.debug("The main component of " + this.getName() + " is an Implementation : " + mComponent.getName());
		    mainImpl = (Implementation) mComponent;		    
		}else if (mComponent!=null && mComponent instanceof Specification) {
		    logger.debug("The main component of " + this.getName() + " is a Specification : " + mComponent.getName());
		    /*
             *  It is a specification to resolve as the main implem. Do not select another composite
             */
            Set<String> constraints = new HashSet<String>();
            constraints.add("(!(" + CST.APAM_COMPOSITETYPE + "=" + CST.V_TRUE + "))");
            mainImpl = CST.apamResolver.resolveSpecByName(dummyComposite, mainComponent, constraints, null);
		}
		
		/*
		 * If we cannot resolve the main implementation, we abort the registration in APAM, taking care of
		 * undoing the partial processing already performed. 
		 */
        if (mainImpl == null) {
            logger.debug("The main component is " + mComponent);
        	unregister();
            throw new InvalidConfiguration("Cannot find main implementation " + mainComponent);
        }
        
        //assert mainImpl != null;
        
        if (! mainImpl.getInCompositeType().contains(this)) deploy(mainImpl) ;
		
        /*
         * Check that the main implementation conforms to the declaration of the composite
         * 
         */
        boolean providesResources = mainImpl.getAllProvidedResources().containsAll(getAllProvidedResources()); 

		/*
		 * If the main implementation is not conforming, we abort the registration in APAM, taking care of
		 * undoing the partial processing already performed. 
		 */
        if (! providesResources) {
        	unregister();
        	throw new InvalidConfiguration("invalid main implementation " + mainImpl.getName() + " for composite type "
                    + getName() + "Main implementation Provided resources " + mainImpl.getDeclaration().getProvidedResources()
                    + "do no provide all the expected resources : " + getSpec().getDeclaration().getProvidedResources());
        }

        //TODO Other control, other than provided resources ?
    }
    
    
    @Override
    public void unregister() {
		/*
		 *  Remove the instances and notify managers
		 */ 
    	super.unregister();

    	/*
    	 * Remove import relationships. 
    	 * NOTE We have to copy the list because we update it while iterating it
    	 */
		for (CompositeType imported : new HashSet<CompositeType>(imports)) {
	        removeImport(imported);
		}

		for (CompositeType importedBy : new HashSet<CompositeType>(invImports)) {
	        ((CompositeTypeImpl)importedBy).removeImport(this);
		}

    	/*
    	 * Remove opposite references from embedding composite types
    	 * 
    	 * TODO May be this should be done at the same type that the contains
    	 * hierarchy, but this will require a refactor of the superclass to 
    	 * have a fine control on the order of the steps.
    	 */
		for (CompositeType inComposite : invEmbedded) {
	        ((CompositeTypeImpl)inComposite).removeEmbedded(this);
		}
		
    	invEmbedded.clear();
    	
		/*
		 * Remove from list of composite types
		 */
		CompositeTypeImpl.compositeTypes.remove(getName());
    }

    /**
     * Deploy (logically) a new implementation into this composite type.
     * 
     * TODO Should this method be in the public API or it is restricted to the
     * resolver and other managers?
     */
    public void deploy(Implementation impl) {
    	
    	/*
    	 * Remove implementation from the unused container if this is the first deployment
    	 */
    	if ( ! impl.isUsed()) {
    		((ImplementationImpl)impl).removeInComposites(CompositeTypeImpl.getRootCompositeType());
    		((CompositeTypeImpl)CompositeTypeImpl.getRootCompositeType()).removeImpl(impl);
    		
    		/*
    		 * If the implementation is composite, it is also embedded in the unused container
    		 */
    		if (impl instanceof CompositeType) {
        		((CompositeTypeImpl)impl).removeInvEmbedded(CompositeTypeImpl.getRootCompositeType());
        		((CompositeTypeImpl)CompositeTypeImpl.getRootCompositeType()).removeEmbedded((CompositeTypeImpl)impl);
    		}
    	}
    	
    	/*
    	 * Add the implementation to this composite 
    	 */
    	((ImplementationImpl)impl).addInComposites(this);
    	this.addImpl(impl);
    	
		/*
		 * Embed in this hierarchy if the implementation is composite
		 */
    	if (impl instanceof CompositeType) {
        	((CompositeTypeImpl)impl).addInvEmbedded(this);
        	this.addEmbedded((CompositeTypeImpl)impl);
    	}
    }
    
    @Override
    protected Composite reify(Composite composite, ApformInstance platformInstance) throws InvalidConfiguration {
    	return new CompositeImpl(composite,platformInstance);
    }

    @Override
    public Implementation getMainImpl() {
        return mainImpl;
    }

    @Override
    public CompositeDeclaration getCompoDeclaration() {
        return (CompositeDeclaration) getDeclaration();
    }

    @Override
    public Set<ManagerModel> getModels() {
        return Collections.unmodifiableSet(models);
    }

    @Override
    public ManagerModel getModel(String managerName) {
        for (ManagerModel model : models) {
            if (model.getManagerName().equals(managerName))
                return model;
        }
        return null;
    }
    
    @Override
    public Set<CompositeType> getImport() {
        return Collections.unmodifiableSet(imports);
    }

    @Override
    public boolean isFriend(CompositeType destination) {
        return imports.contains(destination);
    }
    
    @Override
    public void addImport(CompositeType destination) {
        imports.add(destination);
        ((CompositeTypeImpl) destination).addInvImport(this);
    }

    public boolean removeImport(CompositeType destination) {
        ((CompositeTypeImpl) destination).removeInvImport(this);
        return imports.remove(destination);
    }
    

    public void addInvImport(CompositeType dependent) {
        invImports.add(dependent);
    }

    public boolean removeInvImport(CompositeType dependent) {
        return invImports.remove(dependent);
    }
    
    @Override
    public Set<CompositeType> getEmbedded() {
        return Collections.unmodifiableSet(embedded);
    }

    public void addEmbedded(CompositeType destination) {
        embedded.add(destination);
    }

    public boolean removeEmbedded(CompositeType destination) {
        return embedded.remove(destination);
    }

    public void addInvEmbedded(CompositeType origin) {
        invEmbedded.add(origin);
    }

    public boolean removeInvEmbedded(CompositeType origin) {
        return invEmbedded.remove(origin);
    }

    @Override
    public boolean containsImpl(Implementation impl) {
        return contains.contains(impl);
    }

    @Override
    public Set<Implementation> getImpls() {
        return Collections.unmodifiableSet(contains);
    }

    public void addImpl(Implementation impl) {
        contains.add(impl);
    }

    public void removeImpl(Implementation impl) {
        contains.remove(impl);
    }

    @Override
    public Set<CompositeType> getInvEmbedded() {
        return Collections.unmodifiableSet(invEmbedded);
    }

    @Override
    public String toString() {
        return getName();
    }


}
