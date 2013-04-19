package fr.imag.adele.apam.distriman.discovery;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.jmdns.NetworkTopologyDiscovery;

public class NetworkTopology implements NetworkTopologyDiscovery {

	@Override
	public InetAddress[] getInetAddresses() {
		// TODO Auto-generated method stub
		List<InetAddress> addresses=new ArrayList<InetAddress>();
		
		try {
			for (NetworkInterface ni :  Collections.list(NetworkInterface.getNetworkInterfaces())) {

				for (InetAddress inetAddress : Collections.list(ni.getInetAddresses())) {
					if(!this.useInetAddress(ni, inetAddress)) continue;
					addresses.add(inetAddress);
				}
			}
			
			return addresses.toArray(new InetAddress[0]);
			
		} catch (SocketException e) {
			e.printStackTrace();
		}
		
		return null;

	}

	@Override
	public boolean useInetAddress(NetworkInterface networkInterface,
			InetAddress interfaceAddress) {
		
		if (interfaceAddress instanceof Inet6Address 
			//	|| !interfaceAddress.isLoopbackAddress()
				) return false; 
		
		return true;
	}

	@Override
	public void lockInetAddress(InetAddress arg0) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void unlockInetAddress(InetAddress arg0) {
		// TODO Auto-generated method stub
		
	}

}
