package fr.imag.adele.apam.test.testcases;

import static org.ops4j.pax.exam.CoreOptions.mavenBundle;

import java.util.HashMap;
import java.util.List;

import junit.framework.Assert;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.JUnit4TestRunner;

import fr.imag.adele.apam.CST;
import fr.imag.adele.apam.Composite;
import fr.imag.adele.apam.CompositeType;
import fr.imag.adele.apam.Implementation;
import fr.imag.adele.apam.Instance;
import fr.imag.adele.apam.pax.test.device.DeadsManSwitch;
import fr.imag.adele.apam.pax.test.iface.device.Eletronic;
import fr.imag.adele.apam.pax.test.impl.FailException;
import fr.imag.adele.apam.pax.test.impl.S3GroupAImpl;
import fr.imag.adele.apam.tests.helpers.ExtensionAbstract;

@RunWith(JUnit4TestRunner.class)
public class DynamanDependentTest extends ExtensionAbstract {

	@Override
	public List<Option> config() {

		List<Option> defaultOptions = super.config();
		defaultOptions.add(mavenBundle("fr.imag.adele.apam", "dynaman")
				.version("0.0.1-SNAPSHOT"));

		return defaultOptions;

	}

	@Test
	public void CompositeContentMngtDependencyFailWait() {

		CompositeType cta = (CompositeType) CST.apamResolver.findImplByName(
				null, "composite-a-fail-wait");

		Composite composite_a = (Composite) cta.createInstance(null, null);

		Instance instanceApp1 = composite_a.getMainInst();

		S3GroupAImpl ga1 = (S3GroupAImpl) instanceApp1.getServiceObject();

		ThreadWrapper wrapper = new ThreadWrapper(ga1);
		wrapper.setDaemon(true);
		wrapper.start();

		apam.waitForIt(3000);

		String message = "In case of composite dependency been maked as fail='wait', the thread should be blocked until the dependency is satisfied. During this test the thread did not block.";

		Assert.assertTrue(message, wrapper.isAlive());
	}

	// Require by the test CompositeContentMngtDependencyFailWait
	class ThreadWrapper extends Thread {

		final S3GroupAImpl group;

		public ThreadWrapper(S3GroupAImpl group) {
			this.group = group;
		}

		@Override
		public void run() {
			System.out.println("Element injected:" + group.getElement());
		}

	}

	@Test
	public void CompositeDependencyFailWait() {

		Implementation cta = (Implementation) CST.apamResolver.findImplByName(
				null, "group-a-fail-wait");

		Instance instanceApp1 = cta.createInstance(null, null);

		S3GroupAImpl ga1 = (S3GroupAImpl) instanceApp1.getServiceObject();
		
		ThreadWrapper wrapper = new ThreadWrapper(ga1);
		wrapper.setDaemon(true);
		wrapper.start();

		apam.waitForIt(3000);

		String message = "In case of dependency been maked as fail='wait', the thread should be blocked until the dependency is satisfied. During this test the thread did not block.";

		Assert.assertTrue(message, wrapper.isAlive());
	}
	
	@Test
	public void CompositeFailException() {

		Implementation group_a = (Implementation) CST.apamResolver.findImplByName(
				null, "group-a-fail-exception");
	
		Instance instance_a = (Instance) group_a.createInstance(null,
				null);

		S3GroupAImpl ga1 = (S3GroupAImpl) instance_a.getServiceObject();

		String messageTemplate = "In dependency if we adopt fail='exception' exception='A', the exception A should be throw in case the dependency is not satifiable. %s";

		boolean exception = false;
		boolean exceptionType = false;

		try {

			Eletronic injected = ga1.getElement();
			System.out.println("Element:" + injected);

		} catch (Exception e) {
			exception = true;

			System.err.println("-------------- Exception raised -----------------");
			
			e.printStackTrace();
			
			System.err.println("-------------- /Exception raised -----------------");
			
			if (e instanceof FailException) {
				exceptionType = true;
			}

		}

		String messageException = String.format(messageTemplate,
				"But no exception was thrown");
		String messageExceptionType = String.format(messageTemplate,
				"But the exception thrown was not of the proper type (A)");

		Assert.assertTrue(messageException, exception);
		Assert.assertTrue(messageExceptionType, exceptionType);

	}
	
	@Test
	public void CompositeContentMngtOwnSpecification() {

		CompositeType cta = (CompositeType) CST.apamResolver.findImplByName(
				null, "composite-a-own-specification");

		Composite composite_a = (Composite) cta.createInstance(null, null);

		Implementation dependencyImpl = CST.apamResolver.findImplByName(null,
				"group-a");

		Instance dependencyInstance = dependencyImpl.createInstance(null, null);

		S3GroupAImpl group_a = (S3GroupAImpl) dependencyInstance
				.getServiceObject();

		Instance inst = CST.componentBroker
				.getInstService(group_a.getElement());

		String message = "When a composite declares to own a specification, that means every instance of that specification should be owned by that composite. This test failed, the actual owner composite of that component and the one that declares to be the owner are different";

		Assert.assertTrue(message, inst.getComposite() == composite_a);

	}

	@Test
	public void CompositeWithEagerDependency_05() {
		CompositeType ct1 = (CompositeType) CST.apamResolver.findImplByName(
				null, "S2Impl-composite-eager");

		String message = "During this test, we enforce the resolution of the dependency by signaling dependency as eager='true'. %s";

		Assert.assertTrue(String.format(message,
				"Although, the test failed to retrieve the composite"),
				ct1 != null);

		auxListInstances("instances existing before the test-");

		Instance instance = ct1.createInstance(null,
				new HashMap<String, String>());

		Assert.assertTrue(String.format(message,
				"Although, the test failed to instantiate the composite"),
				instance != null);

		// Force injection (for debuggin purposes)
		// S2Impl im=(S2Impl)instance.getServiceObject();
		// im.getDeadMansSwitch();

		List<Instance> pool = auxLookForInstanceOf(DeadsManSwitch.class
				.getCanonicalName());

		auxListInstances("instances existing after the test-");

		Assert.assertTrue(
				String.format(
						message,
						"Although, there exist no instance of dependence required(DeadsManSwitch.class), which means that it was not injected."),
				pool.size() == 1);

	}

	@Test
	public void CompositeContentMngtDependencyFailException() {

		CompositeType ctroot = (CompositeType) CST.apamResolver.findImplByName(
				null, "composite-a-fail-exception");

		CompositeType cta = (CompositeType) CST.apamResolver.findImplByName(
				null, "composite-a-fail-exception");

		Composite composite_root = (Composite) ctroot
				.createInstance(null, null);

		Composite composite_a = (Composite) cta.createInstance(composite_root,
				null);

		Instance instanceApp1 = composite_a.getMainInst();

		S3GroupAImpl ga1 = (S3GroupAImpl) instanceApp1.getServiceObject();

		String messageTemplate = "In contentMngt->dependency if we adopt fail='exception' exception='A', the exception A should be throw in case the dependency is not satifiable. %s";

		boolean exception = false;
		boolean exceptionType = false;

		try {

			Eletronic injected = ga1.getElement();
			System.out.println("Element:" + injected);

		} catch (Exception e) {
			exception = true;

			System.err.println("-------------- Exception raised -----------------");
			
			e.printStackTrace();
			
			System.err.println("-------------- /Exception raised -----------------");
			
			if (e instanceof FailException) {
				exceptionType = true;
			}

		}

		String messageException = String.format(messageTemplate,
				"But no exception was thrown");
		String messageExceptionType = String.format(messageTemplate,
				"But the exception thrown was not of the proper type (A)");

		Assert.assertTrue(messageException, exception);
		Assert.assertTrue(messageExceptionType, exceptionType);

	}

	@Test
	public void CompositeContentMngtDependencyHide() {

		CompositeType ctaroot = (CompositeType) CST.apamResolver
				.findImplByName(null, "composite-a-hide");

		Composite composite_root = (Composite) ctaroot.createInstance(null,
				null);// composite_root

		CompositeType cta = (CompositeType) CST.apamResolver.findImplByName(
				null, "composite-a-hide");

		Composite composite_a = (Composite) cta.createInstance(composite_root,
				null);// inner composite with hide='true'

		Instance instanceApp1 = composite_a.getMainInst();

		S3GroupAImpl ga1 = (S3GroupAImpl) instanceApp1.getServiceObject();
		// force injection
		ga1.getElement();

		auxListInstances("\t");

		List<Instance> instancesOfImplementation = auxLookForInstanceOf("fr.imag.adele.apam.pax.test.impl.S3GroupAImpl");

		String messageTemplate = "Using hiding into a dependency of a composite should cause the instance of this component to be removed in case of an dependency of such componenent was satisfiable, instead the its instance is still visible. There are %d instances, and should be only 1 (the root composite that encloses the dependency with hide='true')";

		String message = String.format(messageTemplate,
				instancesOfImplementation.size());

		Assert.assertTrue(message, instancesOfImplementation.size() == 1);

	}

}
