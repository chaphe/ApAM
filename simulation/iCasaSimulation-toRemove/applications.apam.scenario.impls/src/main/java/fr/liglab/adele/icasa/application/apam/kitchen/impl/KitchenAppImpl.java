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
package fr.liglab.adele.icasa.application.apam.kitchen.impl;

import fr.imag.adele.apam.ApamComponent;
import fr.imag.adele.apam.Instance;
import fr.imag.adele.apam.message.AbstractConsumer;
import fr.liglab.adele.icasa.device.GenericDevice;
import fr.liglab.adele.icasa.devices.apam.Microwave;
import fr.liglab.adele.icasa.devices.apam.Oven;


public class KitchenAppImpl implements KitchenApp,ApamComponent {

	private Microwave microwave;

	private Oven oven;

	/**
	 * Send kitchen Message
	 */
	private AbstractConsumer<KitchenMessage> sendKitchenMessage;

	@Override
	public void stopAllDevices() {
		if (oven != null) {
			oven.setState(GenericDevice.STATE_DEACTIVATED);
		}
		
		if (microwave != null) {
			microwave.setState(GenericDevice.STATE_DEACTIVATED);
		}
	}

	public void consumeOvenMessage(String event) {
		System.out.println("KitchenApp >> New Message from Oven : " + event);
//		System.out.println("Start keep warm  10s" );
//		oven.keepWarm(10);
	}
	
	public void consumeMicrowaveMessage(String event) {
		System.out.println("KitchenApp >> New Message from Microwave : " + event);
	}

	@Override
	public void startOven() {
		if (oven != null)
			oven.setState(GenericDevice.STATE_ACTIVATED);
	}

	@Override
	public void ovenKeepWarm(int time) {
		if (oven != null)
			oven.keepWarm(time);
	}

	@Override
	public void stopOven() {
		if (oven != null)
			oven.setState(GenericDevice.STATE_ACTIVATED);
	}

	@Override
	public void startMicrowave(int time) {
		if (microwave != null)
			microwave.setCookTime(time);
			microwave.setState(GenericDevice.STATE_ACTIVATED);

	}

	@Override
	public void stopMicrowave() {
		if (microwave != null)
			microwave.setState(GenericDevice.STATE_DEACTIVATED);
	}

	@Override
	public void apamStart(Instance apamInstance) {
		// TODO 		
	}

	@Override
	public void apamStop() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void apamRelease() {
		// TODO Auto-generated method stub
		
	}

}
