package fr.imag.adele.apam.apamAPI;

import java.net.URL;
import java.rmi.RemoteException;
import java.util.Set;

import org.osgi.framework.Filter;
import org.osgi.framework.InvalidSyntaxException;

import fr.imag.adele.am.exception.ConnectionException;
import fr.imag.adele.apam.util.Attributes;
import fr.imag.adele.sam.Specification;

public interface ASMSpecBroker {
    /**
     * 
     * @param specName the *logical* name of that specification; different from SAM.
     *            May be null.
     * @param samSpec : A SAM specification.
     */
    public ASMSpec addSpec(String specName, Specification samSpec, Attributes properties);

    /**
     * Creates a specification. WARNING : this spec may not have any
     * corresponding spec in SAM. It does not try to create one in SAM.
     * 
     * @param compo
     *            the composite in which to create that spec.
     * @param specName
     *            the *logical* name of that specification; different from SAM.
     *            May be null.
     * @param interfaces
     *            the list of interfaces this spec implements
     * @param properties
     *            : The initial properties. return an ASM Specification
     */
    public ASMSpec createSpec(String specName, String[] interfaces, Attributes properties);

    /**
     * Creates and deploys a specification. WARNING : The fact to deploy the
     * specification (the packages containing the interfaces) does not create
     * any spec in SAM. This spec may not have any corresponding spec in SAM. It
     * does not try to create one in SAM.
     * 
     * @param compo the composite in which to create that spec.
     * @param specName the *logical* name of that specification; different from SAM.
     *            May be null.
     * @param url the location of the executable to deploy
     * @param interfaces the list of interfaces this spec implements
     * @param properties : The initial properties. return an ASM Specification
     */
    public ASMSpec createSpec(String specName, URL url, String[] interfaces, Attributes properties);

    /**
     * WARNING : it will also destroy all implems and instances.
     * 
     * @param spec
     *            the spec to delete.
     */
    public void removeSpec(ASMSpec spec);

    /**
     * return the ASM specification associated with that sam specification
     * 
     * @param samImpl
     * @return
     */
    public ASMSpec getSpec(Specification samSpec);

    /**
     * Called from the local machine, it allows to get information of the
     * distant Machine. returns the exported specification that this Abstract
     * Service requires. WARNING : The required abstract services that are not
     * exported are not returned.
     * 
     * @param specification
     *            the specification
     * @return The (exported) abstract services required as known by the distant
     *         machine.
     * @throws ConnectionException
     *             the connection exception
     */
    public Set<ASMSpec> getRequires(ASMSpec specification);

    /**
     * Returns the first abstract service that satisfies the goal. If goal is
     * null all the abstract services are supposed to be matched.
     * 
     * @param goal
     *            the goal
     * @return the abstract service
     * @throws ConnectionException
     *             the connection exception Returns the ExportedSpecification
     *             exported by this Machine that satisfies the goal. If goal is
     *             null, returns an arbitrary exported AbstractService.
     * @throws InvalidSyntaxException
     *             the invalid syntax exception
     */
    public ASMSpec getSpec(Filter goal) throws InvalidSyntaxException;

    /**
     * Returns the specification that implement all and only the provided
     * interfaces. At most one specification can satisfy that requirement (by
     * definition of specification) WARNING : the same specification can be
     * implemented by different technologies (SCM). The list of interfaces is
     * the minimum required to implement a specification.
     * 
     * @param interfaces
     *            : the interfaces of the required specification. The returned
     *            specification must support all the interfaces in the array.
     *            The order in which the interfaces are provided in the array is
     *            nor relevant. NOTE : the SAM specification name is the
     *            concatenation separated by ";" of all the interfaces, ordered
     *            lexicographically. Cannot be null nor empty.
     * 
     * @return the specification
     * @throws ConnectionException
     *             the connection exception Returns the ExportedSpecification
     *             exported by this Machine that satisfies the interfaces.
     */
    public ASMSpec getSpec(String[] interfaces);

    /**
     * Returns *the first* specification that implements the provided
     * interfaces. WARNING : the same interface can be implemented by different
     * specifications, and a specification may implement more than one interface
     * : the first spec found is returned. WARNING : convenient only if a single
     * spec provides that interface; otherwise it is non deterministic.
     * 
     * @param interfaceName
     *            : the name of the interface of the required specification.
     * @return the abstract service
     * @throws ConnectionException
     *             the connection exception Returns the ExportedSpecification
     *             exported by this Machine that satisfies the interfaces.
     */
    public ASMSpec getSpecInterf(String interfaceName);

    /**
     * Returns the specification with the given logical name. WARNING: Name is
     * the *logical* name of that specification; it may be null (if not Apam
     * specific). If the logical name is null, looks for the SAM name of the
     * specifications. NOTE : the SAM specification name is the concatenation
     * separated by ";" of all the interfaces, ordered lexicographically.
     * 
     * @param name
     *            the logical name of the specification, or sam name if no
     *            logical name provided
     * @return the abstract service
     * @throws ConnectionException
     *             the connection exception Returns the ExportedAbstractService
     *             exported by this Machine with this name. If name is null,
     *             returns null.
     */
    public ASMSpec getSpec(String name);

    /**
     * Returns the specification with the given sam name.
     * 
     * @param samName
     *            the sam name of the specification
     * @return the abstract service
     */
    public ASMSpec getSpecSamName(String samName);

    /**
     * Returns all the abstract service. If no abstract service are matched,
     * returns null.
     * 
     * @return the abstract services
     * @throws ConnectionException
     *             the connection exception Returns all the {@link ExportedAbstractService} exported by this Machine. If
     *             none, returns null.
     */

    public Set<ASMSpec> getSpecs();

    /**
     * Returns all the abstract service that satisfies the goal. If goal is null
     * all the abstract services are supposed to be matched. If no abstract
     * service are matched, returns null.
     * 
     * @param goal
     *            the goal
     * @return the abstract services
     * @throws ConnectionException
     *             the connection exception Returns all the {@link ExportedAbstractService} exported by this Machine
     *             that
     *             satisfies the goal. If goal is null, returns all the exported
     *             AbstractService. If none, returns null.
     */
    public Set<ASMSpec> getSpecs(Filter goal) throws InvalidSyntaxException;

    /**
     * Called from the local machine, it allows to get information of the
     * distant Machine. Returns the Abstract Services PID required by that
     * abstract Service on that the Service Implementation implements. WARNING :
     * The required abstract services that are not exported are not returned.
     * 
     * @param specification
     *            the specification
     * @return the require remote
     * @throws RemoteException
     *             *
     * @throws ConnectionException
     *             the connection exception
     * @throws ConnectionException
     *             the connection exception
     */
    public Set<ASMSpec> getRequireRemote(ASMSpec specification);

}
