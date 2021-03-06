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


import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.osgi.framework.Bundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.imag.adele.apam.ApamManagers;
import fr.imag.adele.apam.CST;
import fr.imag.adele.apam.Component;
import fr.imag.adele.apam.Composite;
import fr.imag.adele.apam.DynamicManager;
import fr.imag.adele.apam.Implementation;
import fr.imag.adele.apam.Instance;
import fr.imag.adele.apam.Specification;
import fr.imag.adele.apam.Wire;
import fr.imag.adele.apam.apform.ApformInstance;
import fr.imag.adele.apam.declarations.InstanceDeclaration;
import fr.imag.adele.apam.util.Visible;

public class InstanceImpl extends ComponentImpl implements Instance {

    private static Logger     logger           = LoggerFactory.getLogger(InstanceImpl.class);

    private static final long serialVersionUID = 1L;

    private Implementation    myImpl;
    private Composite         myComposite;

    private final Set<Wire>   wires            = Collections.newSetFromMap(new ConcurrentHashMap<Wire, Boolean>());
    private final Set<Wire>   invWires         = Collections.newSetFromMap(new ConcurrentHashMap<Wire, Boolean>());

    /**
     * This class represents the Apam root instance.
     *
     * This is an APAM concept without mapping at the execution platform level, we build an special
     * apform object to represent it.
     *
     */
    private static class SystemRootInstance implements ApformInstance {

        private final InstanceDeclaration declaration;

        public SystemRootInstance(Implementation rootImplementation, String name) {
            declaration = new InstanceDeclaration(rootImplementation.getImplDeclaration().getReference(), name, null);
        }

        @Override
        public Bundle getBundle() {
            return null;
        }

        @Override
        public InstanceDeclaration getDeclaration() {
            return declaration;
        }

        @Override
        public void setInst(Instance asmInstImpl) {
            throw new UnsupportedOperationException("method not available in root instance");
        }

        @Override
        public void setProperty(String attr, String value) {
            throw new UnsupportedOperationException("method not available in root instance");
        }

        @Override
        public Object getServiceObject() {
            throw new UnsupportedOperationException("method not available in root instance");
        }

        @Override
        public boolean setWire(Instance destInst, String depName) {
            throw new UnsupportedOperationException("method not available in root instance");
        }

        @Override
        public boolean remWire(Instance destInst, String depName) {
            throw new UnsupportedOperationException("method not available in root instance");
        }

        @Override
        public boolean substWire(Instance oldDestInst, Instance newDestInst,
                                 String depName) {
            throw new UnsupportedOperationException("method not available in root instance");
        }

        @Override
        public Instance getInst() {
            throw new UnsupportedOperationException("method not available in root instance");
        }

    }

    /**
     * This is an special constructor only used for the root instance of the system
     */
    protected InstanceImpl(Implementation rootImplementation, String name) throws InvalidConfiguration {
        super(new SystemRootInstance(rootImplementation, name));

        myImpl = rootImplementation;
        //WARNING: this is a trick to have a dummy root instance, both an instance and its composite.
        myComposite = (Composite)this;

        /*
         * NOTE the root instance is automatically registered in Apam in a specific way that
         * allows bootstraping the system
         * 
         */
        if (rootImplementation == CompositeTypeImpl.getRootCompositeType()) {
        	((ImplementationImpl) getImpl()).addInst(this);
        }
    }

    /**
     * Builds a new Apam instance to represent the specified platform instance in the Apam model.
     */
    protected InstanceImpl(Composite composite, ApformInstance apformInst) throws InvalidConfiguration {

        super(apformInst);

        if (composite == null) {
            throw new InvalidConfiguration("Null parent while creating instance");
        }

        Implementation implementation = CST.componentBroker.getImpl(apformInst.getDeclaration().getImplementation()
                .getName());

        if (implementation == null) {
            throw new InvalidConfiguration("Null implementation while creating instance");
        }


        /*
         * reference the implementation and the enclosing composite
         */
        myImpl = implementation;
        myComposite = composite;

    }

    @Override
    public void register(Map<String, String> initialproperties) throws InvalidConfiguration {


        /*
         * Opposite references from implementation and enclosing composite
         */
        ((ImplementationImpl) getImpl()).addInst(this);
        ((CompositeImpl) getComposite()).addContainInst(this);

        /*
         * Terminates the initialization, and computes properties
         */
        finishInitialize(initialproperties);

        /*
         * Add to broker
         */
        ((ComponentBrokerImpl) CST.componentBroker).add(this);

        /*
        * Bind to the underlying execution platform instance
        */
        getApformInst().setInst(this);

        /*
        * Notify managers
        */
        ApamManagers.notifyAddedInApam(this);
    }

    /**
     * remove from ASM It deletes the wires, which deletes the isolated used instances, and transitively. It deleted the
     * invWires, which removes the associated real dependency :
     */
    @Override
    public void unregister() {
        logger.debug("unregister instance " + this);

        /*
        * Remove from broker, and from its composites.
        * After that, it is invisible.
        */
        ((ImplementationImpl) getImpl()).removeInst(this);
        ((CompositeImpl) getComposite()).removeInst(this);

        /*
         * Remove all incoming and outgoing wires (this deletes the associated references at the execution platform level) 
         */
        for (Wire wire : invWires) {
            ((WireImpl) wire).remove();
        }



        /*
        * Unbind from the underlying execution platform instance
        */
        getApformInst().setInst(null);

        /*
        * Do no remove the outgoing wires, in case a Thread is still here.
        * If so, the dependency will be resolved again !
        * Should only remove the invWire ! But weird: wired only in a direction ...
        */

        for (Wire wire : wires) {
            ((WireImpl) wire).remove();
        }

//        /*
//         * Notify managers
//         */
//        ApamManagers.notifyRemovedFromApam(this);
//
//

    }



    /**
     * Change the owner (enclosing composite) of this instance.
     *
     */
    public void setOwner(Composite owner) {

        ((CompositeImpl) getComposite()).removeInst(this);
        this.myComposite = owner;
        ((CompositeImpl) owner).addContainInst(this);
    }

    @Override
    public final boolean isUsed() {
        return !((CompositeImpl) getComposite()).isSystemRoot();
    }

    @Override
    public final ApformInstance getApformInst() {
        return (ApformInstance) getApformComponent();
    }

    @Override
    public final Composite getComposite() {
        return myComposite;
    }

    @Override
    public Composite getAppliComposite() {
        return myComposite.getAppliComposite();
    }

    @Override
    public Implementation getImpl() {
        return myImpl;
    }

    @Override
    public Object getServiceObject() {
        return getApformInst().getServiceObject();
    }

    @Override
    public Specification getSpec() {
        return myImpl.getSpec();
    }

    @Override
    public boolean isSharable() {
        return (getInvWires().isEmpty() || isShared());
    }

    /**
     * returns the connections towards the service instances actually used. return only APAM wires. for SAM wires the
     * sam instance
     */
    @Override
    public Set<Instance> getWireDests(String depName) {
        Set<Instance> dests = new HashSet<Instance>();
        for (Wire wire : wires) {
            if (wire.getDepName().equals(depName)) {
                dests.add(wire.getDestination());
            }
        }
        return dests;
    }

    /**
     */
    @Override
    public Set<Instance> getWireDests() {
        Set<Instance> dests = new HashSet<Instance>();
        for (Wire wire : wires) {
            dests.add(wire.getDestination());
        }
        return dests;
    }

    @Override
    public Set<Wire> getWires() {
        return Collections.unmodifiableSet(wires);
    }

    @Override
    public Set<Wire> getWires(String dependencyName) {
        Set<Wire> dests = new HashSet<Wire>();
        for (Wire wire : wires) {
            if (wire.getDepName().equals(dependencyName)) {
                dests.add(wire);
            }
        }
        return dests;
    }

//    @Override
//    public Set<Wire> getWires(Specification spec) {
//        if (spec == null) {
//            return null;
//        }
//        Set<Wire> w = new HashSet<Wire>();
//        for (Wire wire : wires) {
//            if (wire.getDestination().getSpec() == spec) {
//                w.add(wire);
//            }
//        }
//        return w;
//    }

    @Override
    public boolean createWire(Instance to, String depName, boolean hasConstraints, boolean promotion) {
    	
    	if (!promotion) {
    		assert Visible.isVisible(this, to) ;
    	}
    	
        if ((to == null) || (depName == null)) {
            return false;
        }

        for (Wire wire : wires) { // check if it already exists
            if ((wire.getDestination() == to) && wire.getDepName().equals(depName)) {
                return true;
            }
        }

        // creation
        Wire wire ;
        if (getApformInst().setWire(to, depName)) {
            wire = new WireImpl(this, to, depName, hasConstraints, true);
            wires.add(wire);
            ((InstanceImpl) to).invWires.add(wire);
        } else {
            logger.error("INTERNAL ERROR: wire from " + this + " to " + to
                    + " could not be created in the real instance.");
            return false;
        }

        /*
         *  if the instance was in the unused pull, move it to the from composite.
         *  
         */
        if (!to.isUsed()) {
            ((InstanceImpl) to).setOwner(getComposite());
        }

        // Other relationships to instantiate
        
        if(to.getImpl()!=null)
        	((ImplementationImpl) getImpl()).addUses(to.getImpl());
        
       //TODO distriman: the destination (to) spec being false verification could be avoided in previous step
        if ((SpecificationImpl) getSpec() != null && to.getSpec()!=null) {
            ((SpecificationImpl) getSpec()).addRequires(to.getSpec());
        }

        //Notify Dynamic managers that a new wire has been created
        for (DynamicManager manager : ApamManagers.getDynamicManagers()) {
            manager.addedWire(wire);
        }

        return true;
    }

    //    @Override
    public void removeWire(Wire wire) {
        if (getApformInst().remWire(wire.getDestination(), wire.getDepName())) {
            wires.remove(wire);
            //TODO distriman: check if we have the destination implementation, is this the right way to do it?
            
            if(wire.getDestination().getImpl()!=null)
            	((ImplementationImpl) getImpl()).removeUses(wire.getDestination().getImpl());
            
            //Notify Dynamic managers that a  wire has been deleted. A new resolution can be possible now.
            for (DynamicManager manager : ApamManagers.getDynamicManagers()) {
                manager.removedWire(wire);
            }

        } else {
            logger.error("INTERNAL ERROR: wire from " + this + " to " + wire.getDestination()
                    + " could not be removed in the real instance.");
        }
    }

    public void removeInvWire(Wire wire) {
        invWires.remove(wire);
//        if (invWires.isEmpty()) {
            /*
             * This instance is no longer used.
             * We do not set it unused
             *     setUsed(false);
             *     setOwner(CompositeImpl.getRootAllComposites());
             * 
             * Because it must stay in the same composite since  
             * it may be the target of an "OWN" clause, and must not be changed. In case it will be re-used (local).
             */
//        }
    }

    @Override
    public Set<Wire> getInvWires() {
        return Collections.unmodifiableSet(invWires);
    }

    @Override
    public Set<Wire> getInvWires(String depName) {
        Set<Wire> w = new HashSet<Wire>();
        for (Wire wire : invWires) {
            if ((wire.getDestination() == this) && (wire.getDepName().equals(depName))) {
                w.add(wire);
            }
        }
        return w;
    }

    @Override
    public Wire getInvWire(Instance destInst) {
        if (destInst == null) {
            return null;
        }
        for (Wire wire : invWires) {
            if (wire.getDestination() == destInst) {
                return wire;
            }
        }
        return null;
    }

    @Override
    public Wire getInvWire(Instance destInst, String depName) {
        if (destInst == null) {
            return null;
        }
        for (Wire wire : invWires) {
            if ((wire.getDestination() == destInst) && (wire.getDepName().equals(depName))) {
                return wire;
            }
        }
        return null;
    }

    @Override
    public Set<Wire> getInvWires(Instance destInst) {
        if (destInst == null) {
            return null;
        }
        Set<Wire> w = new HashSet<Wire>();
        for (Wire wire : invWires) {
            if (wire.getDestination() == destInst) {
                w.add(wire);
            }
        }
        return w;
    }


    @Override
    public Set<Component> getMembers() {
        return Collections.emptySet();
    }

    @Override
    public Component getGroup() {
        return myImpl;
    }

}