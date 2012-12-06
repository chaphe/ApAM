package fr.imag.adele.dynamic.manager;

import java.util.Collections;
import java.util.Set;

import fr.imag.adele.apam.Component;
import fr.imag.adele.apam.Composite;
import fr.imag.adele.apam.Implementation;
import fr.imag.adele.apam.Instance;
import fr.imag.adele.apam.declarations.DependencyDeclaration;
import fr.imag.adele.apam.declarations.ImplementationReference;
import fr.imag.adele.apam.declarations.ResourceReference;
import fr.imag.adele.apam.declarations.SpecificationReference;
import fr.imag.adele.apam.impl.ApamResolverImpl;

/**
 * This class is used to represent the pending requests that are waiting for resolution.
 * 
 * There is one subclass of this generic request for each of the stages of the resolution
 * process where the request can be blocked
 * 
 */
public abstract class PendingRequest<T extends Component> {

	/**
	 * The source of the dependency
	 */
	protected final Instance source;
	
	/**
	 * The resolver
	 */
	protected final ApamResolverImpl resolver;
	/**
	 * The dependency to resolve
	 */
	protected final DependencyDeclaration dependency;
	
	/**
	 * The result of the resolution
	 */
	private Set<T> resolution;

	/**
	 * Builds a new pending request reification
	 */
	protected PendingRequest(ApamResolverImpl resolver, Instance source, DependencyDeclaration dependency) {
		this.resolver		= resolver;
		this.source			= source;
		this.dependency		= dependency;
		this.resolution		= null;
	}
	
	public Instance getSource() {
		return source;
	}
	
	/**
	 * The dependency that needs resolution
	 */
	public DependencyDeclaration getDependency() {
		return dependency;
	}
	
	/**
	 * The context in which the resolution is requested
	 */
	public Composite getContext() {
		return source.getComposite();
	}
		
	/**
	 * Block the current thread until a component satisfying the request is available.
	 * 
	 * Resolution must be retried by another thread, and when successful it will notify
	 * this object to unblock the waiting thread.
	 * 
	 */
	public void block() {
		synchronized (this) {
			try {
				/*
				 * wait for resolution
				 */
				while (resolution == null)
					this.wait();
				
				
			} catch (InterruptedException ignored) {
			}
		}
	}

	/**
	 * Tests whether this request is blocked waiting for resolution
	 */
	public synchronized boolean isResoved() {
		return this.resolution != null;
	}

	/**
	 * The result of the resolution
	 */
	public synchronized Set<T> getResolution() {
		return resolution;
	}

	private static ThreadLocal<PendingRequest<?>> current = new ThreadLocal<PendingRequest<?>>();

	private void beginResolve() {
		current.set(this);
	}
	
	private void endResolve() {
		current.set(null);
	}
	
	/**
	 * Whether the current thread is performing a resolution retry
	 */
	public static boolean isRetry() {
		return current() != null;
	}
	
	/**
	 * The request that is being resolved by the current thread
	 */
	public static PendingRequest<?> current() {
		return current.get();
	}
	
	/**
	 * Tries to resolve the request and wakes up the blocked thread
	 */
	public void resolve() {

		/*
		 * avoid multiple concurrent resolutions
		 */
		if (resolution != null)
			return;

		/*
		 * try to resolve
		 */
		synchronized (this) {
			try {
				beginResolve();
				resolution = retry();
				this.notifyAll();
			} finally {
				endResolve();
			}
		}
	}

	
	/**
	 * Retries the resolution of the request
	 */
	protected abstract Set<T> retry();

	
	/**
	 * Decides whether the specified component could potentially resolve this request.
	 * 
	 * This is used as a hint to avoid unnecessarily retrying a resolution that is not
	 * concerned with an event.
	 */
	public abstract boolean isSatisfiedBy(Component candidate);

	
	/**
	 * This class represents an specification resolution request
	 */
	public static class SpecificationResolution extends PendingRequest<Implementation> {
	
		public SpecificationResolution(ApamResolverImpl resolver, Instance source, DependencyDeclaration dependency) {
			super(resolver,source,dependency);
		}
		
		@Override
		protected Set<Implementation> retry() {
			/*
			 * First consider the case the target is a named implementation
			 */
			if (dependency.getTarget() instanceof ImplementationReference<?>) {
				Implementation result = resolver.findImplByDependency(getSource(),dependency);
				return result != null ? Collections.singleton(result) : null;
			}
			
			/*
			 * Next consider resolution by provided resource
			 */
			if (! dependency.isMultiple()) {
				Implementation result = resolver.resolveSpecByResource(getSource(),dependency);
				return result != null ? Collections.singleton(result) : null;
			}
			else {
				return resolver.resolveSpecByResources(getSource(),dependency);
			}
		}
	
		@Override
		public boolean isSatisfiedBy(Component candidate) {
			Implementation implementation = null;
			
			if (candidate instanceof Implementation)
				implementation = (Implementation) candidate;
			
			if (candidate instanceof Instance)
				implementation	= ((Instance) candidate).getImpl();
			
			if (implementation == null)
				return false;
			
			/*
			 * Validate the implementation matches the requested specification
			 */
			boolean valid = false;
			
			if (dependency.getTarget() instanceof ImplementationReference<?>)
				valid = implementation.getDeclaration().getReference().equals(dependency.getTarget());
	
			if (dependency.getTarget() instanceof SpecificationReference)
				valid = implementation.getSpec().getDeclaration().getReference().equals(dependency.getTarget());
	
			if (dependency.getTarget() instanceof ResourceReference)
				valid = implementation.getDeclaration().getProvidedResources().contains(dependency.getTarget());
			
			
			/*
			 * TODO we could also validate constraints but this may be costly, and will be done again by
			 * ApamMan
			 * 
			 * for (String constraint : getDependency().getImplementationConstraints()) {
			 * 		if (!implementation.match(constraint))
			 *			valid = false;
			 * }
			 *
			 */
			
			return valid;
		}

	}

	/**
	 * This class represents an implementation resolution request
	 */
	public static class ImplementationResolution extends PendingRequest<Instance> {
	
		private final Implementation 	implementation;
		
		public ImplementationResolution(ApamResolverImpl resolver, Instance source, Implementation implementation, DependencyDeclaration dependency) {
			super(resolver,source,dependency);
			
			this.implementation	= implementation;
		}
		
		@Override
		protected Set<Instance> retry() {
			if (dependency.isMultiple())
				return resolver.resolveImpls(getSource(), implementation, dependency);
			else
				return Collections.singleton(resolver.resolveImpl(getSource(), implementation, dependency));
		}
	
		@Override
		public boolean isSatisfiedBy(Component candidate) {
			
			if (candidate instanceof Instance)
				return ((Instance) candidate).getImpl().equals(implementation);
			
			
			/*
			 * TODO we could also validate constraints but this may be costly, and will be done again by
			 * ApamMan
			 * 
			 * for (String constraint : getDependency().getInstanceConstraints()) {
			 * 		if (!candidate.match(constraint))
			 *			return false;
			 * }
			 *
			 */
			
			return false;
		}

		
	}


}
