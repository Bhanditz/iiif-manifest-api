package eu.europeana.iiif.web;

import eu.europeana.iiif.service.*;
import eu.europeana.iiif.service.exception.IIIFException;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.protocol.HTTP;
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
import static eu.europeana.iiif.service.CacheUtils.rePackage;
import static eu.europeana.iiif.service.CacheUtils.spicAndSpan;

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
    private static final String ACCEPT          = "Accept";
    private static final String IFNONEMATCH     = "If-None-Match";
    private static final String IFMATCH         = "If-Match";
    private static final String IFMODIFIEDSINCE = "If-Modified-Since";
    private static final String LASTMODIFIED    = "Last-Modified";
    private static final String ETAG            = "ETag";
    private static final String ALLOWED         = "GET, HEAD";
    private static final String ALLOWHEADERS    = "If-Match, If-None-Match, If-Modified-Since";
    private static final String EXPOSEHEADERS   = "Allow, ETag, Last-Modified, Link";
    private static final String ORIGIN          = "Origin";
    private static final String NOCACHE         = "no-cache";
    private static final String MAXAGE          = "600";
    private static final String ANY             = "*";

    private ManifestService manifestService;

    String appVersion;

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
            HttpServletRequest request) throws IIIFException {
        // TODO integrate with apikey service?? (or leave it like this?)

        RecordResponse recordResponse;
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

        // Evaluates the Cache headers and send the appropriate request to the Record API. The Record API's response
        // is contained within the RecordResponse object
        recordResponse = processCacheHeaders(request, id, wskey, recordApi);

        // Evaluate the Record API's response and handle HTTP 304 & 412 statuses.
        // Note that in case an If-None-Match request results in a HTTP 304 response, the cache related headers are
        // also included. In other HTTP 304 & 412 cases only the HTTP status is returned (with empty body)
        if (recordResponse.getHttpStatus() == HttpStatus.NOT_MODIFIED.value()){
            if (recordResponse.isIfNoneMatchRequest()){
                return new ResponseEntity<>(CacheUtils.generateCacheHeaders(recordResponse, appVersion, NOCACHE, ACCEPT),
                                            HttpStatus.NOT_MODIFIED);
            } else {
                return new ResponseEntity<>(HttpStatus.NOT_MODIFIED);
            }
        } else if (recordResponse.getHttpStatus() == HttpStatus.PRECONDITION_FAILED.value()){
            return new ResponseEntity<>(HttpStatus.PRECONDITION_FAILED);
        } else if (recordResponse.getHttpStatus() != HttpStatus.OK.value()){
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        } else { // HTTP 200, generate regular response + body

            String json = recordResponse.getJson();
            HttpHeaders headers = CacheUtils.generateCacheHeaders(recordResponse, appVersion, NOCACHE, ACCEPT);
            CacheUtils.addCorsHeaders(headers, recordResponse);

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
    }

    /**
     * Validates contents of the Accept Header and determines what the IIIF version of the response should be,
     * based on the contents of the that Header and the 'version' GET parameter.
     * IF an Accept header is found AND has a valid format AND contains a Profile parameter:
     * - if the value is valid (either "2" or "3"), return that;
     * - if it contains another value: return "X" (resulting in HTTP 406)
     * ELSE IF the Accept header is empty OR does not contain a Format parameter: check the 'format' parameter:
     * - if it is supplied, use that;
     * - if not, return the default value "2".
     * ELSE IF the Accept header has an invalid format: return "X" (resulting in HTTP 406)
     * @param request HttpServletRequest
     * @param format  String
     * @return result String representing status of Accept Header
     */
    private String processAcceptHeader(HttpServletRequest request, String format) {
        String result = "0";
        String accept = request.getHeader(ACCEPT);
        if (validateAcceptFormat(accept) && StringUtils.isNotEmpty(accept)) {
            Matcher m = acceptProfilePattern.matcher(accept);
            if (m.find()) {
                String profiles = m.group(1);
                if (profiles.toLowerCase(Locale.getDefault()).contains(MEDIA_TYPE_IIIF_V3)) {
                    result = "3";
                } else if (profiles.toLowerCase(Locale.getDefault()).contains(MEDIA_TYPE_IIIF_V2)) {
                    result = "2";
                } else {
                    result = "X";
                }
            }
        }  else if (StringUtils.isNotEmpty(accept)) {
            result = "X";
        }
        if (StringUtils.equalsIgnoreCase(result, "0")) {
            if (StringUtils.isBlank(format)){
                result = "2";
            } else {
                result = format;
            }
        }
        return result;
    }

    private boolean validateAcceptFormat(String accept){
        return (StringUtils.containsIgnoreCase(accept, "*/*")) ||
               (StringUtils.containsIgnoreCase(accept, MEDIA_TYPE_JSON)) ||
               (StringUtils.containsIgnoreCase(accept, MEDIA_TYPE_JSONLD));
    }

    private RecordResponse processCacheHeaders(HttpServletRequest request,
                                                    String id, String wskey, URL recordApi) throws IIIFException {

        String reqIfNoneMatch   = request.getHeader(IFNONEMATCH);
        String reqIfMatch       = request.getHeader(IFMATCH);
        String reqIfModSince    = request.getHeader(IFMODIFIEDSINCE);
        String reqOrigin        = request.getHeader(ORIGIN);
        appVersion              = manifestService.getSettings().getAppVersion();
        String matchingVersionETag;

        if (StringUtils.isNotBlank(reqIfNoneMatch)){
            if (StringUtils.equals(CacheUtils.spicAndSpan(reqIfNoneMatch), ANY)){
                return manifestService.getRecordJson(id, wskey, recordApi, ANY, null, reqIfModSince, reqOrigin);
            }
            matchingVersionETag = CacheUtils.doesAnyOfTheseMatch(reqIfNoneMatch, appVersion);
            // matchingVersionETag will be "x" if BASE64 won't decode
            if (StringUtils.isBlank(matchingVersionETag) || StringUtils.equalsIgnoreCase(matchingVersionETag, "x")) {
                return manifestService.getRecordJson(id, wskey, recordApi);
            } else {
                return manifestService.getRecordJson(id, wskey, recordApi,
                                                     matchingVersionETag, null, reqIfModSince, reqOrigin);
            }
        } else if (StringUtils.isNotBlank(reqIfMatch)){
            if (StringUtils.equals(CacheUtils.spicAndSpan(reqIfNoneMatch), ANY)){
                return manifestService.getRecordJson(id, wskey, recordApi, null, ANY, reqIfModSince, reqOrigin);
            }
            matchingVersionETag = CacheUtils.doesAnyOfTheseMatch(reqIfMatch, appVersion);
            if (StringUtils.isNotBlank(matchingVersionETag) &&  // not empty if eTag contains matching version, delegate
                !StringUtils.equalsIgnoreCase(matchingVersionETag, "x")) {
                return manifestService.getRecordJson(id, wskey, recordApi, null,
                                                     matchingVersionETag, reqIfModSince, reqOrigin);
            } else {
                return new RecordResponse(412); // will be "x" if BASE64 won't decode
            }
        }

        // in all other cases:
        return manifestService.getRecordJson(id, wskey, recordApi, null, null, reqIfModSince, reqOrigin);
    }

}
