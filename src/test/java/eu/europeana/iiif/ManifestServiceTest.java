package eu.europeana.iiif;

import com.fasterxml.jackson.core.JsonProcessingException;
import eu.europeana.iiif.model.v2.ManifestV2;
import eu.europeana.iiif.model.v3.ManifestV3;
import eu.europeana.iiif.service.ManifestService;
import eu.europeana.iiif.service.exception.IIIFException;
import eu.europeana.iiif.service.exception.InvalidApiKeyException;
import eu.europeana.iiif.service.exception.RecordNotFoundException;
import org.apache.commons.logging.LogFactory;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import java.io.IOException;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests the general flow of generating output (getRecord, generate the manifest and serialize it) for both versions
 * of the manifest (v2 and v3)
 * @author Patrick Ehlert on 18-1-18.
 */

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class ManifestServiceTest {


    private static final String TEST1_CHILD_RECORD_ID = "/9200408/BibliographicResource_3000117822022";
    private static final String TEST2_PARENT_RECORD_ID = "/9200356/BibliographicResource_3000100340004";
    private static final String TEST3_CHILD_RECORD_ID = "/9200385/BibliographicResource_3000117433317";
    private static final String TEST4_PARENT_RECORD_ID = "/9200359/BibliographicResource_3000116004551";
    private static final String APIKEY = "api2demo";

    private static ManifestService ms;

    @BeforeClass
    public static void setup() {
        ms = new ManifestService();
    }

    private String getRecord(String recordId) throws IIIFException {
        String json = ms.getRecordJson(recordId, APIKEY);
        assertNotNull(json);
        assertTrue(json.contains("\"about\":\""+recordId+"\""));
        return json;
    }

    private ManifestV2 getManifestV2(String recordId) throws IIIFException {
        ManifestV2 m = ms.generateManifestV2(getRecord(recordId));
        assertNotNull(m);
        assertTrue(m.getId().contains(recordId));
        return m;
    }

    private ManifestV3 getManifestV3(String recordId) throws IIIFException {
        ManifestV3 m = ms.generateManifestV3(getRecord(recordId));
        assertNotNull(m);
        assertTrue(m.getId().contains(recordId));
        return m;
    }

    /**
     * Test retrieval of record json data
     * @throws IIIFException
     */
    @Test
    public void testGetJsonRecord() throws IIIFException {
        getRecord(TEST1_CHILD_RECORD_ID);
    }

    /**
     * Test whether we get a RecordNotFoundException if we provide an incorrect id
     * @throws IIIFException
     */
    @Test(expected = RecordNotFoundException.class)
    public void testGetJsonRecordNotExist() throws IIIFException {
        getRecord("/NOTEXISTS/123");
    }

    /**
     * Test whether we get a InvalidApiKeyException if we provide an incorrect api key
     * @throws IIIFException
     */
    @Test(expected = InvalidApiKeyException.class)
    public void testGetJsonRecordApikeyInvalid() throws IIIFException {
        ms.getRecordJson(TEST2_PARENT_RECORD_ID, "INVALID");
    }

    /**
     * Test generation of Manifest for version 2
     * @throws IIIFException
     */
    @Test
    public void testGetManifestV2() throws IIIFException {
        getManifestV2(TEST1_CHILD_RECORD_ID);
    }

    /**
     * Test generation of Manifest for version 3
     * @throws IIIFException
     */
    @Test
    public void testGetManifestV3() throws IIIFException {
        getManifestV3(TEST2_PARENT_RECORD_ID);
    }

    /**
     * Test serializing manifest for version 2
     * @throws IIIFException
     */
    @Test
    public void testSerializeJsonLdV2() throws IIIFException {
        String recordId = TEST3_CHILD_RECORD_ID;
        String jsonLd = ms.serializeManifest(getManifestV2(recordId));
        assertNotNull(jsonLd);
        LogFactory.getLog(ManifestService.class).error("jsonld v2 = " + jsonLd);
        assertTrue(jsonLd.contains("\"id\" : \"https://iiif.europeana.eu/presentation" + recordId + "/manifest"));
    }

    /**
     * Test serializing manifest for version 3
     * @throws IIIFException
     */
    @Test
    public void testSerializeJsonLdV3() throws IIIFException {
        String recordId = TEST4_PARENT_RECORD_ID;
        String jsonLd = ms.serializeManifest(getManifestV3(recordId));
        assertNotNull(jsonLd);
        LogFactory.getLog(ManifestService.class).error("jsonld v3 = "+jsonLd);
        assertTrue(jsonLd.contains("\"id\" : \"https://iiif.europeana.eu/presentation"+recordId+"/manifest"));
    }


}
