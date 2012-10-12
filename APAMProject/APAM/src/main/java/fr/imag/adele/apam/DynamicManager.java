package fr.imag.adele.apam;

//import fr.imag.adele.sam.Instance;

public interface DynamicManager extends DependencyManager {

    /**
     * The manager asks to be notified of the creation of an instance or implem in the ASM (or un-hidden)
     * 
     */
    public abstract void addedInApam(Component newComponent);

    /**
     * The manager asks to be notified of the removing of a an instance or implem (or hidden)from the ASM
     * 
     */
    public abstract void removedFromApam(Component lostComponent);

}