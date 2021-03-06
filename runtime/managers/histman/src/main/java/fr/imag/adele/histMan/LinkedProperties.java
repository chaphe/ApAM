package fr.imag.adele.histMan;

import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Properties;
import java.util.Set;

public class LinkedProperties extends Properties {

    private static final long serialVersionUID = 1L;

    public LinkedProperties() {
    }

    public LinkedProperties(Properties defaults) {
        super(defaults);
    }

    private final LinkedHashSet<Object> keys = new LinkedHashSet<Object>();

    @Override
    public Set<Object> keySet() {
        return keys;
    }

    @Override
    public Enumeration<Object> keys() {
        return Collections.<Object> enumeration(keys);
    }

    @Override
    public Object put(Object key, Object value) {
        keys.add(key);
        return super.put(key, value);
    }
}
