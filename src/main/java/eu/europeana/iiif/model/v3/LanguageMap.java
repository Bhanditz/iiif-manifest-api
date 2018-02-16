package eu.europeana.iiif.model.v3;

import java.io.Serializable;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author Patrick Ehlert
 * Created on 24-01-2018
 */
public class LanguageMap extends LinkedHashMap<String, String[]> implements Serializable {

    private static final long serialVersionUID = -7678917507346373456L;

    /**
     * @return textual representation of the contents of the languagemap (for debugging purposes)
     */
    public String toString() {
        StringBuilder s = new StringBuilder();
        s.append('(');
        for (Map.Entry<String, String[]> entry : this.entrySet()) {
            if (s.length() > 1) {
                s.append(", ");
            }
            String language = entry.getKey();
            String[] values = entry.getValue();
            s.append('{').append(language).append('=').append(Arrays.toString(values)).append('}');
        }
        s.append(')');
        return s.toString();
    }
}
