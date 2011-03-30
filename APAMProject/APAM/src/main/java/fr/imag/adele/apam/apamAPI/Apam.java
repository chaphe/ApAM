package fr.imag.adele.apam.apamAPI;

import java.net.URL;
import java.util.Map;
import java.util.Set;

import fr.imag.adele.apam.ManagerModel;
import fr.imag.adele.apam.util.Attributes;

public interface Apam {
	
	/**
	 * creates an application from an existing implementation found in SAM.
	 * Does not start the application. Call execute for that.
	 * @param compositeName the name of the application : the name of the root composite
	 * @param models optional : the list of models for that root composite.
	 * @param samImplName the implementation name as known by Sam.
	 */
	public Composite createAppli (String appliName,  Set <ManagerModel> models, String samImplName, 
			String implName, String specName, Map<String, Object> properties) ;
	/**
	 * creates an instance of the main implementation associated with the root composite. i.e. starts the application.
	 */
	public void execute (Map<String, Object> properties) ;
	
	/**
	 * Creates an application from scratch, by deploying an implementation.
	 * First creates the root composites (compositeName), associates its models (modles).
	 * Then install an implementation (implName) from its URL, considered as the application Main.
	 * @param compositeName The name of the root composite.
	 * @param models The manager models
	 * @param implName The logical name for the Application Main implementation
	 * @param url Location of the Main executable.
	 * @param type Type of packaging.
	 * @param specName optional : the logical name of the associated specification
	 * @param properties The initial properties for the Implementation.
	 * @return
	 */
	public Composite createAppli(String appliName,  Set <ManagerModel> models, String implName, URL url, String type, String specName, Map<String, Object> properties) ;

	public Composite getAppli () ;
	public ASMImpl getAppliMain  ()  ;
	
}
