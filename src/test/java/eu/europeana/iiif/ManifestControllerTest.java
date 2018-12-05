package eu.europeana.iiif;

import eu.europeana.iiif.model.Definitions;
import eu.europeana.iiif.model.v2.ManifestV2;
import eu.europeana.iiif.model.v3.ManifestV3;
import eu.europeana.iiif.service.ManifestService;
import eu.europeana.iiif.service.ManifestSettings;
import eu.europeana.iiif.service.RecordResponse;
import eu.europeana.iiif.web.ManifestController;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;

import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.CoreMatchers.*;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.BDDMockito.given;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests if the ManifestController returns the correct (version of) responses with the appropriate headers
 * @author Patrick Ehlert
 * Created on 06-03-2018
 */
@RunWith(SpringJUnit4ClassRunner.class)
@TestPropertySource("classpath:iiif-test.properties")
@WebMvcTest(ManifestController.class)
public class ManifestControllerTest {

    private static final String JSON_RECORD         = "{ \"object\": {\"timestamp_update\":\"2015-10-28T07:28:00Z\"} }";
    private static final String JSONLD_V2_OUTPUT    = "{Manifest : JSONLD-V2}";
    private static final String JSONLD_V3_OUTPUT    = "{Manifest : JSONLD-V3}";
    private static final String TIMESTAMP_UPDATE    = "Wed, 28 Oct 2015 07:28:00 GMT";
    private static final String TIMESTAMP_AFTER     = "Tue, 12 Jul 2016 11:07:32 GMT";
    private static final String TIMESTAMP_BEFORE    = "Wed, 18 Apr 2012 04:54:16 GMT";
    private static final String ETAG_HEADER_V2      = "W/\"f0524d47eba1d4f83dbc02cd1786a4e12edfa2a187b0dab730ec9310fa01868d\"";
    private static final String ETAG_HEADER_V3      = "W/\"545f96ddfbdef6a91d60d0176229e8a2abb73f20694b425bd428573f522e0bbe\"";
    private static final String ETAG_HEADER_FALSE   = "W/\"ca3d67df3ee77ece353fd070203c47a43ea383558df10a4ab0cd2ce4b46d7643\"";

    // v.2 eTag handling: BASE64 encoded => W/"{record_api_ETag}|v1.0-test
    private static final String ETAG_RECORDAPI      = "W/\"basgitaarenklapsigaar\"";
    private static final String ETAG_CONSTRUCTED    = "W/\"Vy8iYmFzZ2l0YWFyZW5rbGFwc2lnYWFyInx2MS4wLXRlc3Q=\"";
    private static final String ETAG_FALSE          = "W/\"foeihoesuffendstaatgijdaar\"";
    private static final String ETAG_FALSECONSTR    = "W/\"Vy8iZm9laWhvZXN1ZmZlbmRzdGFhdGdpamRhYXIifHYxLjAtdGVzdA==\"";

    private static final String ALLOWED_METHODS     = "GET, HEAD";
    private static final String ALLOWED_HEADERS     = "If-Match, If-None-Match, If-Modified-Since";
    private static final String EXPOSED_HEADERS     = "Allow, Vary, ETag, Last-Modified";
    private static final String MAX_AGE             = "1000";

    private RecordResponse record200Response = new RecordResponse(JSON_RECORD);
    private RecordResponse record304Response = new RecordResponse(JSON_RECORD);
    private RecordResponse record412Response = new RecordResponse(JSON_RECORD);
    private RecordResponse recordCORSResponse = new RecordResponse(JSON_RECORD);

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ManifestService manifestService;
    @MockBean
    private ManifestSettings manifestSettings;

    @Before
    public void setup() throws Exception {

        given(manifestSettings.getAppVersion()).willReturn("v1.0-test");
        record200Response.setHttpStatus(HttpStatus.OK.value());
        record200Response.setETagHeader(ETAG_RECORDAPI);
        record200Response.setLastModifiedHeader(TIMESTAMP_UPDATE);
        record304Response.setHttpStatus(HttpStatus.NOT_MODIFIED.value());
        record304Response.setETagHeader(ETAG_RECORDAPI);
        record304Response.setLastModifiedHeader(TIMESTAMP_UPDATE);
        record412Response.setHttpStatus(HttpStatus.PRECONDITION_FAILED.value());
        recordCORSResponse.setCorsHeaders(ALLOWED_METHODS, ALLOWED_HEADERS, EXPOSED_HEADERS, MAX_AGE);
        recordCORSResponse.setHttpStatus(HttpStatus.OK.value());


        // mock v2 and v3 manifest responses
        ManifestV2 manifest2 = new ManifestV2("/1/2", "/1/2");
        ManifestV3 manifest3 = new ManifestV3("/1/2", "/1/2");
        given(manifestService.getRecordJson("/1/2", "test")).willReturn(record200Response);
        given(manifestService.getRecordJson("/1/2", "test", null)).willReturn(record200Response);
        given(manifestService.getRecordJson("/1/2", "test", null
                , null, null, null, null)).willReturn(record200Response);
        given(manifestService.getRecordJson("/1/2", "test", null
                , null, ETAG_RECORDAPI, null, null)).willReturn(record200Response);
        given(manifestService.getRecordJson("/1/2", "test", null
                , null, ETAG_FALSE, null, null)).willReturn(record412Response);
        given(manifestService.getRecordJson("/1/2", "test", null
                , ETAG_RECORDAPI, null, null, null)).willReturn(record304Response);
        given(manifestService.getRecordJson("/1/2", "test", null
                , ETAG_FALSE, null, null, null)).willReturn(record200Response);
        given(manifestService.getRecordJson("/1/2", "test", null
                , null, null, TIMESTAMP_BEFORE, null)).willReturn(record200Response);
        given(manifestService.getRecordJson("/1/2", "test", null
                , null, null, TIMESTAMP_AFTER, null)).willReturn(record304Response);
        given(manifestService.getRecordJson("/1/2", "test", null
                , null, null, null, "test")).willReturn(recordCORSResponse);
        given(manifestService.generateManifestV2(eq(JSON_RECORD), anyBoolean(), any())).willReturn(manifest2);
        given(manifestService.generateManifestV3(eq(JSON_RECORD), anyBoolean(), any())).willReturn(manifest3);
        given(manifestService.serializeManifest(manifest2)).willReturn(JSONLD_V2_OUTPUT);
        given(manifestService.serializeManifest(manifest3)).willReturn(JSONLD_V3_OUTPUT);
        given(manifestService.getSettings()).willReturn(manifestSettings);
    }

    /**
     * Basic manifest test (no parameters)
     * Default we expect a v2 manifest
     */
    @Test
    public void testManifest() throws Exception {
        this.mockMvc.perform(get("/presentation/1/2/manifest").param("wskey", "test"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().json(JSONLD_V2_OUTPUT));
    }

    /**
     * Test if (the correct) error is thrown if no api key is present
     */
    @Test
    public void testManifestNoApikey() throws Exception {
        this.mockMvc.perform(get("/presentation/1/2/manifest"))
                .andExpect(status().is4xxClientError());
    }

    /**
     * Test if we handle accept headers properly
     * @throws Exception
     */
    @Test
    public void testManifestAcceptHeader() throws Exception {
        // first try v2 header
        this.mockMvc.perform(get("/presentation/1/2/manifest").param("wskey", "test")
                .header("Accept", "application/json; profile=\""+Definitions.MEDIA_TYPE_IIIF_V2+"\""))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("profile=\""+Definitions.MEDIA_TYPE_IIIF_V2+"\"")))
                .andExpect(header().string("eTag", notNullValue()))
                .andExpect(content().json(JSONLD_V2_OUTPUT));

        // then try v3
        this.mockMvc.perform(get("/presentation/1/2/manifest").param("wskey", "test")
                .header("Accept", "application/ld+json;profile=\""+Definitions.MEDIA_TYPE_IIIF_V3+"\""))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("profile=\""+Definitions.MEDIA_TYPE_IIIF_V3+"\"")))
                .andExpect(header().string("eTag", notNullValue()))
                .andExpect(content().json(JSONLD_V3_OUTPUT));

        // check if we get v2 if there is an unknown, but otherwise valid accept
        this.mockMvc.perform(get("/presentation/1/2/manifest").param("wskey", "test")
                .header("Accept", "application/json;profile=X"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("profile=\""+Definitions.MEDIA_TYPE_IIIF_V2+"\"")))
                .andExpect(content().json(JSONLD_V2_OUTPUT));

        // finally check if we get a 406 for unsupported accept headers
        this.mockMvc.perform(get("/presentation/1/2/manifest").param("wskey", "test")
                .header("Accept", "application/xml"))
                .andExpect(status().isNotAcceptable());
    }

    /**
     * Test if the controller also returns the proper Content-type if the version is passed through the 'format' GET
     * parameter instead of via the Accept Header (fixed in #EA-978_fix+EA-1200-changes)
     * @throws Exception
     */
    @Test
    public void testContentTypeHeaderWithFormat() throws Exception {
        // first try format=2
        this.mockMvc.perform(get("/presentation/1/2/manifest")
                                     .param("wskey", "test")
                                     .param("format", "2")
                                     .header("Accept", "application/ld+json"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Type", containsString("profile=\""+Definitions.MEDIA_TYPE_IIIF_V2+"\"")))
                    .andExpect(header().string("eTag", notNullValue()))
                    .andExpect(content().json(JSONLD_V2_OUTPUT));

        // then try format=3
        this.mockMvc.perform(get("/presentation/1/2/manifest")
                                     .param("wskey", "test")
                                     .param("format", "3")
                                     .header("Accept", "application/ld+json"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Type", containsString("profile=\""+Definitions.MEDIA_TYPE_IIIF_V3+"\"")))
                    .andExpect(header().string("eTag", notNullValue()))
                    .andExpect(content().json(JSONLD_V3_OUTPUT)).andDo(print());
    }

    /**
     * Check if we get the proper cross origin headers
     * @throws Exception
     */
    @Test
    public void testManifestCrossOrigin() throws Exception {
        this.mockMvc.perform(get("/presentation/1/2/manifest").param("wskey", "test")
                .header("Accept", "application/json; profile=\""+Definitions.MEDIA_TYPE_IIIF_V2+"\"")
                .header("Origin", "test"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Methods", is(ALLOWED_METHODS)))
                .andExpect(header().string("Access-Control-Allow-headers", is(ALLOWED_HEADERS)))
                .andExpect(header().string("Access-Control-Expose-Headers", is(EXPOSED_HEADERS)))
                .andExpect(header().string("Access-Control-Max-Age", is(MAX_AGE)));
    }

    /**
     * Check if the If-Modified-Since header is handled properly
     * @throws Exception
     */
    @Test
    public void testManifestIfModifiedSince() throws Exception {
        // test after last-modified date
        this.mockMvc.perform(get("/presentation/1/2/manifest").param("wskey", "test")
                    .header("Accept", "application/json; profile=\""+Definitions.MEDIA_TYPE_IIIF_V2+"\"")
                    .header("If-Modified-Since", TIMESTAMP_AFTER))
                    .andExpect(status().isNotModified())
                    .andExpect(header().string("Last-Modified", equalTo(TIMESTAMP_UPDATE)));
        // test before last-modified date
        this.mockMvc.perform(get("/presentation/1/2/manifest").param("wskey", "test")
                    .header("Accept", "application/json; profile=\""+Definitions.MEDIA_TYPE_IIIF_V3+"\"")
                    .header("If-Modified-Since", TIMESTAMP_BEFORE))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Last-Modified", equalTo(TIMESTAMP_UPDATE)));
    }

    /**
     * Check if the If-None-Match header is handled properly
     * @throws Exception
     */
    @Test
    public void testManifestIfNoneMatch() throws Exception {
        // retrieve eTag value
        MvcResult result = this.mockMvc.perform(get("/presentation/1/2/manifest").param("wskey", "test")
                    .header("Accept", "application/json; profile=\""+Definitions.MEDIA_TYPE_IIIF_V2+"\""))
                    .andReturn();
        String eTag = result.getResponse().getHeader("eTag");

        // supply the same eTag value, expect HTTP 304
        this.mockMvc.perform(get("/presentation/1/2/manifest").param("wskey", "test")
                    .header("Accept", "application/json; profile=\""+Definitions.MEDIA_TYPE_IIIF_V2+"\"")
                    .header("If-None-Match", eTag))
                    .andExpect(header().string("eTag", equalTo(ETAG_CONSTRUCTED)))
                    .andExpect(status().isNotModified());

        // supply another eTag value will result in HTTP 200
        this.mockMvc.perform(get("/presentation/1/2/manifest").param("wskey", "test")
                    .header("Accept", "application/json; profile=\""+Definitions.MEDIA_TYPE_IIIF_V2+"\"")
                    .header("If-None-Match", ETAG_HEADER_FALSE))
                    .andExpect(header().string("eTag", equalTo(ETAG_CONSTRUCTED)))
                    .andExpect(status().isOk());
    }


    /**
     * Check if the If-Match header is handled properly
     * @throws Exception
     */
    @Test
    public void testManifestIfMatch() throws Exception {
        // retrieve eTag value
        MvcResult result = this.mockMvc.perform(get("/presentation/1/2/manifest").param("wskey", "test")
                    .header("Accept", "application/json; profile=\""+Definitions.MEDIA_TYPE_IIIF_V3+"\""))
                    .andReturn();
        String eTag = result.getResponse().getHeader("eTag");
        System.err.println("first eTag = "+eTag);

        // supply the same eTag value, expect HTTP 200
        this.mockMvc.perform(get("/presentation/1/2/manifest").param("wskey", "test")
                    .header("Accept", "application/json; profile=\""+Definitions.MEDIA_TYPE_IIIF_V3+"\"")
                    .header("If-Match", eTag))
                    .andExpect(header().string("eTag", equalTo(ETAG_CONSTRUCTED)))
                    .andExpect(status().isOk());

        // supply another eTag value will result in HTTP 412 -- NOTE that this does no longer return the eTag header!
        this.mockMvc.perform(get("/presentation/1/2/manifest").param("wskey", "test")
                    .header("Accept", "application/json; profile=\""+Definitions.MEDIA_TYPE_IIIF_V3+"\"")
                    .header("If-Match", ETAG_FALSECONSTR))
                    .andExpect(status().isPreconditionFailed());
    }
}
