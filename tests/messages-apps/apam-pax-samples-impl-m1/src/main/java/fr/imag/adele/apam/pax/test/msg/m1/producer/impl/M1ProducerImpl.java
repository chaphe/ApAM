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
package fr.imag.adele.apam.pax.test.msg.m1.producer.impl;

import java.util.Queue;

import fr.imag.adele.apam.message.Message;
import fr.imag.adele.apam.pax.test.msg.M1;
import fr.imag.adele.apam.pax.test.msg.M2;
import fr.imag.adele.apam.pax.test.msg.M3;
import fr.imag.adele.apam.pax.test.msg.device.EletronicMsg;
import org.osgi.framework.BundleContext;

import fr.imag.adele.apam.Instance;

public class M1ProducerImpl
{
	Boolean isOnInitCallbackCalled=false;
	Boolean isOnRemoveCallbackCalled=false;
	
	String stateInternal;
	String stateNotInternal;

	Queue<EletronicMsg> simpleDevice110vQueue;
	
	Queue<M2> m2Queue;
    Queue<M3> m3Queue;
    
    Queue<EletronicMsg> eletronicMsgQueue;
    
    Queue<Message<EletronicMsg>> eletronicMessageQueue;
    
    Queue<EletronicMsg> eletronicMsgConstraintsQueue;
    
    Queue<EletronicMsg> devicePreference110vQueue;
    
    Queue<EletronicMsg> deviceConstraint110vQueue;
    
    BundleContext context;
    
    public M1ProducerImpl(BundleContext context){
    	this.context=context;
    }

    public M1 produceM1(String msg){
        return new M1(msg);
    }


    public String whoami()
    {
        return this.getClass().getName();
    }
    
    public void start(){
    	System.out.println("Starting:"+this.getClass().getName());
    	isOnInitCallbackCalled=true;
    }
    
    public void stop(){
    	System.out.println("Stopping:"+this.getClass().getName());
    	isOnRemoveCallbackCalled=true;
    }
    
    public void bind(Instance instance){
    	System.out.println("Starting:"+this.getClass().getName());
    	isOnInitCallbackCalled=true;
    }
    
    public void unbind(Instance instance){
    	System.out.println("Stopping:"+this.getClass().getName());
    	isOnRemoveCallbackCalled=true;
    }

	public Queue<EletronicMsg> getSimpleDevice110vMsg() {
		return simpleDevice110vQueue;
	}

	public Queue<EletronicMsg> getEletronicMsgQueue() {
		return eletronicMsgQueue;
	}
	public Queue<Message<EletronicMsg>> getEletronicMessageQueue() {
		return eletronicMessageQueue;
	}

	public Queue<EletronicMsg> getEletronicMsgConstraintsQueue() {
		return eletronicMsgConstraintsQueue;
	}

	public String getStateNotInternal() {
		return stateNotInternal;
	}

	public void setStateNotInternal(String stateNotInternal) {
		this.stateNotInternal = stateNotInternal;
	}

	public String getStateInternal() {
		return stateInternal;
	}

	public void setStateInternal(String stateInternal) {
		this.stateInternal = stateInternal;
	}

	public Queue<EletronicMsg> getDevicePreference110vQueue() {
		return devicePreference110vQueue;
	}

	public Queue<EletronicMsg> getDeviceConstraint110vQueue() {
		return deviceConstraint110vQueue;
	}

	public Boolean getIsOnInitCallbackCalled() {
		return isOnInitCallbackCalled;
	}

	public void setIsOnInitCallbackCalled(Boolean isOnInitCallbackCalled) {
		this.isOnInitCallbackCalled = isOnInitCallbackCalled;
	}

	public Boolean getIsOnRemoveCallbackCalled() {
		return isOnRemoveCallbackCalled;
	}

	public void setIsOnRemoveCallbackCalled(Boolean isOnRemoveCallbackCalled) {
		this.isOnRemoveCallbackCalled = isOnRemoveCallbackCalled;
	}

	public BundleContext getContext() {
		return context;
	}

	public void setContext(BundleContext context) {
		this.context = context;
	}

	public Queue<M2> getM2Queue() {
		return m2Queue;
	}

	public Queue<M3> getM3Queue() {
		return m3Queue;
	}

}