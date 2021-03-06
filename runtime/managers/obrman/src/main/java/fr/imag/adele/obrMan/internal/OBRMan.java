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
package fr.imag.adele.obrMan.internal;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.felix.bundlerepository.Repository;
import org.apache.felix.bundlerepository.RepositoryAdmin;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.imag.adele.apam.ApamManagers;
import fr.imag.adele.apam.CST;
import fr.imag.adele.apam.Component;
import fr.imag.adele.apam.CompositeType;
import fr.imag.adele.apam.Dependency;
import fr.imag.adele.apam.DependencyManager;
import fr.imag.adele.apam.Implementation;
import fr.imag.adele.apam.Instance;
import fr.imag.adele.apam.ManagerModel;
import fr.imag.adele.apam.Resolved;
import fr.imag.adele.apam.Specification;
import fr.imag.adele.apam.declarations.DependencyDeclaration;
import fr.imag.adele.apam.declarations.InterfaceReference;
import fr.imag.adele.apam.declarations.MessageReference;
import fr.imag.adele.apam.declarations.ResolvableReference;
import fr.imag.adele.apam.declarations.SpecificationReference;
import fr.imag.adele.apam.util.ApamFilter;
import fr.imag.adele.obrMan.OBRManCommand;
import fr.imag.adele.obrMan.internal.OBRManager.Selected;

public class OBRMan implements DependencyManager, OBRManCommand {

    // Link compositeType with it instance of obrManager
    private final Map<String, OBRManager> obrManagers;

    // iPOJO injected
    private RepositoryAdmin               repoAdmin;

//    private Apam                          apam;

    private final Logger                  logger = LoggerFactory.getLogger(OBRMan.class);

    private final BundleContext           m_context;

    private long timeout = 10000;

    /**
     * OBRMAN activated, register with APAM
     */

    public OBRMan(BundleContext context) {
        m_context = context;
        obrManagers = new HashMap<String, OBRManager>();
    }

    public void start() {
        ApamManagers.addDependencyManager(this, 3);
//        logger.info("[OBRMAN] started");
    }

    public void stop() {
        ApamManagers.removeDependencyManager(this);
        obrManagers.clear();
//        logger.info("[OBRMAN] stopped");
    }

    static List<String> onLoadingResource = new ArrayList<String>();

    /**
     * Instal ans instantiate the selected bundle, and return the component.
     * If forced = false (default) does not try to install if the component is allready existing.
     * 
     * @param selected
     * @return
     */
    private Component installInstantiate(Selected selected) {
        if (selected == null)
            return null;

        String name = selected.getComponentName();
        fr.imag.adele.apam.Component c = CST.componentBroker.getComponent(name);
        // Check if already deployed
        if (c == null) {

            if (bundleInactif(selected.resource.getSymbolicName())) {
                logger.info("The bundle " + selected.resource.getSymbolicName() + " is already installed!");
                return null;
            }
            // deploy selected resource
            boolean deployed = selected.obrManager.deployInstall(selected);
            if (!deployed) {
                System.err.print("could not install resource ");
                ObrUtil.printRes(selected.resource);
                return null;
            }
            // waiting for the component to be ready in Apam.
            c = CST.componentBroker.getWaitComponent(name,timeout);
            if (c!=null &&c instanceof Implementation){
                for (String instanceName : selected.getInstancesOfSelectedImpl()) {
                    CST.componentBroker.getWaitComponent(instanceName,timeout);
                }
            }

                 
        } else { // do not install twice.
            // It is a logical deployment. The already existing component is not visible !
            // System.err.println("Logical deployment of : " + name + " found by OBRMAN but allready deployed.");
        }

        return c;

    }


    public boolean bundleInactif(String symbolicName) {
        Bundle[] bunldes = m_context.getBundles();
        for (Bundle bundle : bunldes) {
            if (bundle.getSymbolicName() != null && bundle.getSymbolicName().equals(symbolicName)) {
                if (bundle.getState() == Bundle.ACTIVE || bundle.getState() == Bundle.STARTING
                        || bundle.getState() == Bundle.UNINSTALLED) {
                    return false;
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public String getName() {
        return CST.OBRMAN;
    }

    // at the end
    @Override
    public void
            getSelectionPath(Instance client, DependencyDeclaration dep, List<DependencyManager> involved) {
        involved.add(involved.size(), this);
    }

    @Override
    public Instance resolveImpl(Instance client, Implementation impl, Dependency dep) {
        return null;
    }

    @Override
    public Set<Instance> resolveImpls(Instance client, Implementation impl, Dependency dep) {
        return null;
    }

    @Override
    public int getPriority() {
        return 3;
    }

    @Override
    public void newComposite(ManagerModel model, CompositeType compositeType) {
        OBRManager obrManager;
        if (model == null) { // if no model for the compositeType, set the root composite model
            obrManager = searchOBRManager(compositeType);
        } else {
            try {// try to load the compositeType model
                LinkedProperties obrModel = new LinkedProperties();
                obrModel.load(model.getURL().openStream());
                obrManager = new OBRManager(this, compositeType.getName(), repoAdmin, obrModel);
            } catch (IOException e) {// if impossible to load the model for the compositeType, set the root composite
                                     // model
                logger.error("Invalid OBRMAN Model. Cannot be read stream " + model.getURL(), e.getCause());
                obrManager = searchOBRManager(compositeType);
            }
        }
        obrManagers.put(compositeType.getName(), obrManager);
    }

    // interface manager
    private Implementation resolveSpec(CompositeType compoType, Dependency dep) {

//            Set<String> constraints, List<String> preferences) {
    	ResolvableReference resource = dep.getTarget() ;
		Set<ApamFilter> constraints = dep.getImplementationConstraintFilters() ;
		List<ApamFilter> preferences = dep.getInstancePreferenceFilters() ;

        // Find the composite OBRManager
        OBRManager obrManager = searchOBRManager(compoType);
        if (obrManager == null)
            return null;

        // temporary ??
//        if (preferences == null)
//            preferences = new ArrayList<String>();
//        preferences.add("(apam-composite=true)");
//        // end
//        if (constraints == null) {
//        	constraints = new HashSet <String> () ;
//        }
        ApamFilter f = ApamFilter.newInstance("(" + CST.COMPONENT_TYPE + "=" + CST.IMPLEMENTATION + ")");
        if (f != null)
         dep.getImplementationConstraintFilters().add(f);

        fr.imag.adele.obrMan.internal.OBRManager.Selected selected = null;
        Implementation impl = null;
        if (resource instanceof SpecificationReference) {
            selected = obrManager.lookFor(CST.CAPABILITY_COMPONENT, "(provide-specification*>"
                    + resource.as(SpecificationReference.class).getName() + ")",
                    constraints, preferences);
        }
        if (resource instanceof InterfaceReference) {
            selected = obrManager.lookFor(CST.CAPABILITY_COMPONENT, "(provide-interfaces*>" // =*;"
                    + resource.as(InterfaceReference.class).getJavaType() + ")", // ";*)"
                    constraints, preferences);
        }
        if (resource instanceof MessageReference) {
            selected = obrManager.lookFor(CST.CAPABILITY_COMPONENT, "(provide-messages*>"
                    + resource.as(MessageReference.class).getJavaType() + ")",
                    constraints, preferences);
        }
        if (selected != null) {
            impl = (Implementation) installInstantiate(selected);
            return impl;
        }
        return null;
    }

    private OBRManager searchOBRManager(CompositeType compoType) {
        OBRManager obrManager = null;

        // in the case of root composite, compoType = null
        if (compoType != null) {
            obrManager = obrManagers.get(compoType.getName());
        }

        // Use the root composite if the model is not specified
        if (obrManager == null) {
            obrManager = obrManagers.get(CST.ROOT_COMPOSITE_TYPE);
            if (obrManager == null) { // If the root manager was never been initialized
                // lookFor root.OBRMAN.cfg and create obrmanager for the root composite in a customized location
                String rootModelurl = m_context.getProperty(ObrUtil.ROOT_MODEL_URL);
                try {// try to load root obr model from the customized location
                    if (rootModelurl != null) {
                        URL urlModel = (new File(rootModelurl)).toURI().toURL();
                        setInitialConfig(urlModel);
                    } else {
                        LinkedProperties obrModel = new LinkedProperties();
                        customizedRootModelLocation();
                        obrModel.put(ObrUtil.LOCAL_MAVEN_REPOSITORY, "true");
                        obrModel.put(ObrUtil.DEFAULT_OSGI_REPOSITORIES, "true");
                        obrManager = new OBRManager(this, CST.ROOT_COMPOSITE_TYPE, repoAdmin, obrModel);
                        obrManagers.put(CST.ROOT_COMPOSITE_TYPE, obrManager);
                    }
                } catch (Exception e) {// if failed to load customized location, set default properties for the root
                                       // model
                    logger.error("Invalid Root URL Model. Cannot be read stream " + rootModelurl, e.getCause());
                    LinkedProperties obrModel = new LinkedProperties();
                    customizedRootModelLocation();
                    obrModel.put(ObrUtil.LOCAL_MAVEN_REPOSITORY, "true");
                    obrModel.put(ObrUtil.DEFAULT_OSGI_REPOSITORIES, "true");
                    obrManager = new OBRManager(this, CST.ROOT_COMPOSITE_TYPE, repoAdmin, obrModel);
                    obrManagers.put(CST.ROOT_COMPOSITE_TYPE, obrManager);
                }
            }
        }
        return obrManager;
    }

    private void customizedRootModelLocation() {

    }

    @Override
    public Resolved resolveDependency(Instance client, Dependency dep, boolean needsInstances) {
    	Implementation impl = resolveSpec(client, dep);
    	if (impl == null)
    		return null;
    	
        Set<Implementation> implementations = new HashSet<Implementation>();
        implementations.add(impl);
        Set<Instance> instances = null;
        if (needsInstances){
            instances = impl.getInsts();
        }
        return new Resolved (implementations, instances);
    }

    private Implementation resolveSpec(Instance client, Dependency dep) {
        return resolveSpec(client.getComposite().getCompType(), dep);
    }

    private <C extends Component> C findByName(CompositeType compoType, String componentName, Class<C> kind) {
        if (componentName == null)
            return null;

        // Find the composite OBRManager
        OBRManager obrManager = searchOBRManager(compoType);
        if (obrManager == null)
            return null;

        Selected selected = obrManager.lookFor(CST.CAPABILITY_COMPONENT, "(name=" + componentName + ")", null, null);
        fr.imag.adele.apam.Component c = installInstantiate(selected);
        if (c == null)
            return null;
        if (!kind.isAssignableFrom(c.getClass())) {
            logger.error("ERROR : " + componentName + " is found but is not a " + kind.getCanonicalName());
            return null;
        }

        // @SuppressWarnings("")
        return kind.cast(c);
    }

    @Override
    public Component findComponentByName(Instance client, String componentName) {
        return findByName(client.getComposite().getCompType(), componentName, fr.imag.adele.apam.Component.class);
    }

    @Override
    public Specification findSpecByName(Instance client, String specName) {
        return findByName(client.getComposite().getCompType(), specName, fr.imag.adele.apam.Specification.class);
    }

    @Override
    public Implementation findImplByName(Instance client, String implName) {
        return findByName(client.getComposite().getCompType(), implName, fr.imag.adele.apam.Implementation.class);
    }

    @Override
    public Instance findInstByName(Instance client, String instName) {
        return findByName(client.getComposite().getCompType(), instName, fr.imag.adele.apam.Instance.class);
    }

    public OBRManager getOBRManager(String compositeTypeName) {
        return obrManagers.get(compositeTypeName);
    }

    @Override
    public void notifySelection(Instance client, ResolvableReference resName, String depName, Implementation impl,
            Instance inst, Set<Instance> insts) {
        // Do not care
    }

    public String getDeclaredOSGiOBR() {
        return m_context.getProperty(ObrUtil.OSGI_OBR_REPOSITORY_URL);
    }

    @Override
    public Set<String> getCompositeRepositories(String compositeTypeName) {
        Set<String> result = new HashSet<String>();
        OBRManager obrmanager = getOBRManager(compositeTypeName);
        if (obrmanager == null)
            return result;

        for (Repository repository : obrmanager.getRepositories()) {
            result.add(repository.getURI());
        }
        return result;
    }

    @Override
    public void setInitialConfig(URL modellocation) throws IOException {
        LinkedProperties obrModel = new LinkedProperties();
        if (modellocation != null) {
            obrModel.load(modellocation.openStream());
            OBRManager obrManager = new OBRManager(this, CST.ROOT_COMPOSITE_TYPE, repoAdmin, obrModel);
            obrManagers.put(CST.ROOT_COMPOSITE_TYPE, obrManager);
        } else {
            throw new IOException("URL is null");
        }
    }

    @Override
    public ComponentBundle findBundle(CompositeType compoType, String bundleSymbolicName, String componentName) {
        if (bundleSymbolicName == null || componentName == null)
            return null;

        // Find the composite OBRManager
        OBRManager obrManager = searchOBRManager(compoType);
        if (obrManager == null)
            return null;

        return obrManager.lookForBundle(bundleSymbolicName, componentName);
    }

    /**
     * Update resources from repositories
     * @param compositeName the name of the composite to update or *
     */
    public boolean updateRepos(String compositeName) {
        if (compositeName == null) return false;
        OBRManager obrmanager=null ;
        if (compositeName.equals("*")) {
            for (OBRManager obrManager2 : obrManagers.values()) {
                obrManager2.updateListOfResources(repoAdmin);
            }
            return true;
        } else if (compositeName.equals("root")) {
            obrmanager = getOBRManager(CST.ROOT_COMPOSITE_TYPE);
        } else {
            obrmanager = getOBRManager(compositeName);
        }
        if (obrmanager==null) return false;
        obrmanager.updateListOfResources(repoAdmin);
        return true;
    }


}
