package eu.europeana.iiif.service;

import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpHeaders;
import eu.europeana.iiif.service.exception.IllegalArgumentException;
import java.util.Base64;

/**
 * Utility class to facilitate handling If-Modified-Since, If-None-Match and If-Match request caching
 * @author Patrick Ehlert
 * Created on 03-10-2018
 */
public class CacheUtils {

    private CacheUtils() {
        // empty constructor to prevent initialization
    }

    private static String encodeBase64ETag(String originalEtag, String manifestApiVersion, String separator, boolean weakETag) {
        String contents = originalEtag + separator + manifestApiVersion;
        String newETag = "\"" + Base64.getEncoder().encodeToString(contents.getBytes()) + "\"";
        if (weakETag) {
            return "W/" + newETag;
        }
        return newETag;
    }

    /**
     * BASE64-decodes the input String (without W/ prefix and / or quotes!) and splits the result in two parts
     * (separated by either a pipe character '|', a comma ',' or a whitespace) to obtain the SHA256-hashed original
     * eTag value plus the Manifest API version
     * @param eTag retrieved from the request's If-None-Match or If-Match header
     * @return String[2] containing [0] eTag and [1] Manifest API version
     */
    private static String[] decodeBase64ETag(String eTag) throws IllegalArgumentException{
        String decoded = new String(Base64.getDecoder().decode(eTag));
        for (String separator : new String[]{"|", ",", " "}){
            if (StringUtils.containsAny(decoded, separator)){
                return StringUtils.splitByWholeSeparator(decoded, separator);
            }
        }
        throw new IllegalArgumentException("no valid separator character found in ETag");
    }

    /**
     * Generate the default headers for sending a response with HTTP caching
     * @param response      required, RecordResponse object containing values received from Record API
     * @param version       required, IIIF manifest API version, needed to re-encode the eTag with
     * @param cacheControl  optional, if not null then a Cache-Control header is added
     * @param vary          optional, if not null, then a Vary header is added
     * @return              default HttpHeaders required for HTTP caching
     * TODO move Cache control to the Spring Boot security configuration when that's implemented
     */
    public static HttpHeaders generateCacheHeaders(RecordResponse response, String version, String cacheControl, String vary) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("eTag", encodeBase64ETag(response.getETagHeader(), version, "|", true));
        headers.add("Last-Modified", response.getLastModifiedHeader());
        if (cacheControl != null) {
            headers.add("Cache-Control", cacheControl);
        }
        if (vary != null) {
            headers.add("Vary", vary);
        }
        return headers;
    }

    public static HttpHeaders addCorsHeaders(HttpHeaders headers, RecordResponse response) {
        if (StringUtils.isNotBlank(response.getAcAllowMethodsHeader())){
            headers.add("Access-Control-Allow-Methods", response.getAcAllowMethodsHeader());
        }
        if (StringUtils.isNotBlank(response.getAcAllowHeadersHeader())){
            headers.add("Access-Control-Allow-Headers", response.getAcAllowHeadersHeader());
        }
        if (StringUtils.isNotBlank(response.getAcExposeHeadersHeader())){
            headers.add("Access-Control-Expose-Headers", response.getAcExposeHeadersHeader());
        }
        if (StringUtils.isNotBlank(response.getAcMaxAgeHeader())){
            headers.add("Access-Control-Max-Age", response.getAcMaxAgeHeader());
        }
        return headers;
    }

    /**
     * Supports multiple values presented within If-None-Match / If-Match headers
     * @param  value  value of the request header
     * @return Associated eTag from the BASE64-encoded pair, of which the other part matches appVersion
     *         If none found, returns empty string ""
     */
    public static String doesAnyOfTheseMatch(String value, String appVersion){
        if (StringUtils.isNoneBlank(value, appVersion)){
            return headerVersionMatcher(value, appVersion);
        }
        return "";
    }

    /**
     * For every comma-separated value found in parameter header: BASE64-decode into pairs representing IIIF Manifest
     * API version and a SHA256-hashed eTag. If API version matches parameter appVersion, return the associated
     * eTag value. Otherwise, return empty string "".
     * @param header     If-None-Matches or If-Matches header value
     * @param appVersion String containing IIIF Manifest API version
     * @return the associated eTag string for a matching IIIF Manifest API version
     */
    // check for the version contained within parameter value only
    private static String headerVersionMatcher(String header, String appVersion){
        if (StringUtils.isNoneBlank(header, appVersion)){
            for (String value : StringUtils.stripAll(StringUtils.split(header, ","))){
                try {
                    String[] decodedBase64ETag = decodeBase64ETag(spicAndSpan(value));
                    if (StringUtils.equalsIgnoreCase(decodedBase64ETag[1], appVersion)){
                        return decodedBase64ETag[0];
                    }
                } catch (IllegalArgumentException e ) {
                    return "x";
                }
            }
        }
        return "";
    }

    public static String spicAndSpan(String header){
        return StringUtils.remove(StringUtils.stripStart(header, "W/"), "\"");
    }

}
