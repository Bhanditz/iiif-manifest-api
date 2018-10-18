package eu.europeana.iiif.web;

import eu.europeana.iiif.model.Definitions;
import eu.europeana.iiif.service.EdmManifestMapping;
import eu.europeana.iiif.service.CacheUtils;
import eu.europeana.iiif.service.ManifestService;
import eu.europeana.iiif.service.ValidateUtils;
import eu.europeana.iiif.service.exception.IIIFException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.net.URL;
import java.time.ZonedDateTime;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static eu.europeana.iiif.model.Definitions.*;

/**
 * Rest controller that handles manifest requests
 *
 * @author Patrick Ehlert
 * Created on 06-12-2017
 */
@RestController
public class ManifestController {

    /* for parsing accept headers */
    private static final Pattern acceptProfilePattern = Pattern.compile("profile=\"(.*?)\"");
    private static final String ACCEPT = "Accept";
    private static final String IFNONEMATCH = "If-None-Match";
    private static final String IFMATCH = "If-None-Match";
    private static final String IFMODIFIEDSINCE = "If-Modified-Since";

    private ManifestService manifestService;

    public ManifestController(ManifestService manifestService) {
        this.manifestService = manifestService;
    }

    /**
     * Handles manifest requests
     *
     * @param collectionId (required field)
     * @param recordId     (required field)
     * @param wskey        apikey (required field)
     * @param version      (optional) indicates which IIIF version to generate, either '2' or '3'
     * @param recordApi    (optional) alternative recordApi baseUrl to use for retrieving record data
     * @param fullTextApi  (optional) alternative fullTextApi baseUrl to use for retrieving record data
     * @return JSON-LD string containing manifest
     * @throws IIIFException when something goes wrong during processing
     */
    @SuppressWarnings("squid:S00107") // too many parameters -> we cannot avoid it.

    @GetMapping(value = "/presentation/{collectionId}/{recordId}/manifest")
    public ResponseEntity<String> manifestRequest(
            @PathVariable String collectionId,
            @PathVariable String recordId,
            @RequestParam(value = "wskey", required = true) String wskey,
            @RequestParam(value = "format", required = false) String version,
            @RequestParam(value = "recordApi", required = false) URL recordApi,
            @RequestParam(value = "fullText", required = false, defaultValue = "true") Boolean addFullText,
            @RequestParam(value = "fullTextApi", required = false) URL fullTextApi,
            HttpServletRequest request,
            HttpServletResponse response) throws IIIFException {
        // TODO integrate with apikey service?? (or leave it like this?)

        String id = "/" + collectionId + "/" + recordId;
        ValidateUtils.validateWskeyFormat(wskey);
        ValidateUtils.validateRecordIdFormat(id);

        if (recordApi != null) {
            ValidateUtils.validateApiUrlFormat(recordApi);
        }
        if (fullTextApi != null) {
            ValidateUtils.validateApiUrlFormat(fullTextApi);
        }

        // validates Accept header, looks for version in Profile and version GET parameter
        String acceptHeaderStatus = processAcceptHeader(request, version);
        if (StringUtils.equalsIgnoreCase(acceptHeaderStatus, "X")){
            return new ResponseEntity<>(HttpStatus.NOT_ACCEPTABLE);
        } else {
            version = acceptHeaderStatus;
        }

        checkCacheHeaders(request);

        String json = manifestService.getRecordJson(id, wskey, recordApi);
        ZonedDateTime lastModified = EdmManifestMapping.getRecordTimestampUpdate(json);
        String           eTag = generateETag(id, lastModified, version);
        HttpHeaders   headers = CacheUtils.generateCacheHeaders("no-cache", eTag, lastModified, "Accept");
        ResponseEntity cached = CacheUtils.checkCached(request, headers, lastModified, eTag);
        if (cached != null) {
            return cached;
        }

        Object manifest;
        if ("3".equalsIgnoreCase(version)) {
            manifest = manifestService.generateManifestV3(json, addFullText, fullTextApi);
            headers.add("Content-Type", MEDIA_TYPE_IIIF_JSONLD_V3);
        } else {
            manifest = manifestService.generateManifestV2(json, addFullText, fullTextApi); // fallback option
            headers.add("Content-Type", MEDIA_TYPE_IIIF_JSONLD_V2);
        }
        return new ResponseEntity<>(manifestService.serializeManifest(manifest), headers, HttpStatus.OK);
    }




    /**
     * If Accept header is found && has a valid format && contains a Profile parameter:
     * - if value = "2" or "3", return that; if other values: return "X".
     * Else if Accept header is empty or does not contain Format parameter:
     * - check if HTTP 'format' parameter is given. Yes, use that; no: return "2".
     * If Accept header has invalid format: return "X"
     * @param request HttpServletRequest
     * @param format  String
     * @return result String representing status of Accept Header
     */
    // found and valid, use that; if invalid,
    // return HTTP 406
    private String processAcceptHeader(HttpServletRequest request, String format) {
        String result = "0";
        String accept = request.getHeader(ACCEPT);
        if (validateAcceptFormat(accept) && StringUtils.isNotEmpty(accept)) {
            Matcher m = acceptProfilePattern.matcher(accept);
            if (m.find()) { // found a Profile parameter in the Accept header
                String profiles = m.group(1);
                if (profiles.toLowerCase(Locale.getDefault()).contains(MEDIA_TYPE_IIIF_V3)) {
                    result = "3";
                } else if (profiles.toLowerCase(Locale.getDefault()).contains(MEDIA_TYPE_IIIF_V2)) {
                    result = "2";
                } else {
                    result = "X"; // Profile parameter matches neither "2" or "3" => HTTP 406
                }
            }
        }  else if (StringUtils.isNotEmpty(accept)) { // validateAcceptFormat(accept) = false => HTTP 406
            result = "X";
        }
        // if result == "0": request header is empty, or does not contain a Profile parameter
        if (StringUtils.equalsIgnoreCase(result, "0")) {
            if (StringUtils.isBlank(format)){
                result = "2";    // if format not given, fall back to default "2"
            } else {
                result = format; // else use the format parameter
            }
        }
        return result;
    }

    private boolean validateAcceptFormat(String accept){
        return (StringUtils.containsIgnoreCase(accept, "*/*")) ||
               (StringUtils.containsIgnoreCase(accept, MEDIA_TYPE_JSON)) ||
               (StringUtils.containsIgnoreCase(accept, MEDIA_TYPE_JSONLD));
    }

    private String generateETag(String recordId, ZonedDateTime recordUpdated, String iiifVersion) {
        StringBuilder hashData = new StringBuilder(recordId);
        hashData.append(recordUpdated.toString());
        hashData.append(manifestService.getSettings().getAppVersion());
        hashData.append(iiifVersion);
        return CacheUtils.generateETag(hashData.toString(), true);
    }



    public void checkCacheHeaders(HttpServletRequest request){
        String requestedETag = "";
        String requestedManifestVersion = "";

        // first check whether If-None-Match or If-Match headers are present
        if (StringUtils.isNotBlank(request.getHeader(IFNONEMATCH)) ||
            StringUtils.isNotBlank(request.getHeader(IFMATCH))){
            // If-None-Match or If-Match are present
            String[] cacheHeaders = {IFNONEMATCH, IFMATCH};
            for (String cacheHeader : cacheHeaders) {
                if (StringUtils.isNotBlank(request.getHeader(cacheHeader))){
                    String[] decodedBase64ETag = CacheUtils.decodeBase64ETag(cacheHeader);
                    requestedETag = decodedBase64ETag[0];
                    requestedManifestVersion = decodedBase64ETag[1];
                    break;
                }
            }
            // check if manifest api version string from eTag matches current value (implicit null check)
            if (StringUtils.equalsIgnoreCase(requestedManifestVersion, manifestService.getSettings().getAppVersion())){
                // reported version is equal to the current Manifest API version
                // Action: the Record's staleness needs to be checked
            } else {
                // reported version is NOT equal to the current Manifest API version (and possibly null)
                // get a new response from the Record API, no need to check the reported SHA256 eTag

                // Call Record API, CHECK:

                // IF recorddata == stale THEN the Record API sends an updated response to the IIIF API who uses that
                // to construct a JSON response, and sends that back with the received updated Record API ETag,
                // BASE64-encoded together with the current IIIFversion

                // ELSE recorddata are still valid: the Record will send the NOT CHANGED header to the IIIF
                // Manifest, who will relay this message including the original BASE64 encoded ETag back to the client.

            }
            // now check if the request contains "If-Modified-Since" header
        } else if (StringUtils.isNotBlank(request.getHeader(IFMODIFIEDSINCE))) {
            // ==> call Record API
            // IF record.timestamp_updated was changed since IFMODIFIEDSINCE, it sends updated record back
            // with eTag and all. Action: build manifest, BASE64-package eTag with the IIIF version and relay back

            // ELSE record.timestamp_updated NOT changed since IFMODIFIEDSINCE: the Record API only sends back a
            // HTTP status. Action: relay this status back to the client, but no eTag can be sent.

        } else {
            // no http cache headers sent; so get a new response from the Record API, create manifest & re-package the
            // SHA256 eTag + current manifest API version

            // That's all folks!

        }

    }


//    private String versionFromAcceptHeader(HttpServletRequest request) {
//        String result = "2"; // default version if no accept header is present
//        String accept = request.getHeader("Accept");
//        if (StringUtils.isNotEmpty(accept)) {
//            Matcher m = acceptProfilePattern.matcher(accept);
//            if (m.find()) {
//                String profiles = m.group(1);
//                if (profiles.toLowerCase(Locale.getDefault()).contains(Definitions.MEDIA_TYPE_IIIF_V3)) {
//                    result = "3";
//                } else {
//                    result = "2";
//                }
//            }
//        }
//        return result;
//    }
//
//    private boolean isAcceptHeaderOK(HttpServletRequest request) {
//        String accept = request.getHeader("Accept");
//        return (StringUtils.isBlank(accept)) ||
//                (StringUtils.containsIgnoreCase(accept, "*/*")) ||
//                (StringUtils.containsIgnoreCase(accept, MEDIA_TYPE_JSON)) ||
//                (StringUtils.containsIgnoreCase(accept, MEDIA_TYPE_JSONLD));
//    }

}
