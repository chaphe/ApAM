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
package fr.imag.adele.apam.apform.impl;


import java.util.Map;
import java.util.Properties;

import org.apache.felix.ipojo.ConfigurationException;
import org.apache.felix.ipojo.HandlerManager;
import org.apache.felix.ipojo.IPojoContext;
import org.apache.felix.ipojo.metadata.Element;
import org.osgi.framework.BundleContext;

import fr.imag.adele.apam.Apam;
import fr.imag.adele.apam.CST;
import fr.imag.adele.apam.Specification;
import fr.imag.adele.apam.apform.Apform2Apam;
import fr.imag.adele.apam.apform.ApformImplementation;
import fr.imag.adele.apam.apform.ApformInstance;
import fr.imag.adele.apam.apform.ApformSpecification;
import fr.imag.adele.apam.declarations.ImplementationDeclaration;
import fr.imag.adele.apam.impl.ComponentBrokerImpl;
import fr.imag.adele.apam.impl.ComponentImpl;

public class ApformImplementationImpl extends ApformComponentImpl implements ApformImplementation {

    /**
     * The specification provided by this implementation
     */
    private ApformSpecification specification;

    /**
     * Build a new factory with the specified metadata
     *
     * @param context
     * @param metadata
     * @throws ConfigurationException
     */
    public ApformImplementationImpl(BundleContext context, Element metadata) throws ConfigurationException {
        super(context, metadata);
    }

    @Override
    public void check(Element element) throws ConfigurationException {
        super.check(element);
        specification = null;
    }

    @Override
    public ImplementationDeclaration getDeclaration() {
        return (ImplementationDeclaration) super.getDeclaration();
    }

    /**
     * Get the provided specification representation
     */
    @Override
    public ApformSpecification getSpecification() {
        return specification;
    }


    /**
     * Register this implementation with APAM
     */
    protected void bindToApam(Apam apam) {

        /*
           * Cross-reference to provided interface, if already installed in APAM
           */
        if (getDeclaration().getSpecification() != null) {
            String specName = getDeclaration().getSpecification().getName();
            Specification provided = CST.componentBroker.getSpec(specName);
            if (provided != null && provided.getApformSpec() != null)
                specification = provided.getApformSpec();
        }

        Apform2Apam.newImplementation(this);
    }

    /**
     * Unregister this implementation from APAM
     *
     * @param apam
     */
    protected void unbindFromApam(Apam apam) {
        //Apform2Apam.vanishImplementation(getName());
        ComponentBrokerImpl.disappearedComponent(getName()) ;

    }


    private final ThreadLocal<Boolean> insideApamCall = new ThreadLocal<Boolean>() {
        @Override
        protected Boolean initialValue() {
            return false;
        };
    };

    private final boolean isApamCall() {
        return insideApamCall.get();
    }

    /**
     * Creates an instance of the implementation, and initialize its properties with the set of
     * provided properties.
     *
     * NOTE this method is called when an instance is created by the APAM platform (explicitly by
     * the API or implicitly by a dependency resolution)
     */
    @Override
    public ApformInstance createInstance(Map<String, String> initialproperties) throws ComponentImpl.InvalidConfiguration {
        try {

            ApformInstanceImpl instance = null;

            try {
                insideApamCall.set(true);
                Properties configuration = new Properties();
                if (initialproperties != null)
                    configuration.putAll(initialproperties);
                instance = (ApformInstanceImpl) createComponentInstance(configuration);
            } finally {
                insideApamCall.set(false);
            }

            return instance;

        } catch (Exception cause) {
            throw new ComponentImpl.InvalidConfiguration(cause);
        }

    }


    /**
     * Creates the iPOjo instance corresponding to a newly created native APAM instance.
     *
     * NOTE this method can be called by APAM or from an iPojo instance declaration
     */
    @Override
    public ApformInstanceImpl createApamInstance(IPojoContext context, HandlerManager[] handlers) {
        return new ApformInstanceImpl(this, isApamCall(), context, handlers);
    }

    @Override
    public boolean hasInstrumentedCode() {
        return true;
    }

    @Override
    public boolean isInstantiable() {
        return true;
    }

    @Override
    public void setProperty(String attr, String value) {
        // TODO change factory publication?
    }

}