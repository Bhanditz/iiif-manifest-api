package eu.europeana.iiif.service;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpHeaders;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * Utility class to facilitate handling If-Modified-Since, If-None-Match and If-Match request caching
 * @author Patrick Ehlert
 * Created on 03-10-2018
 */
public class CacheUtils {

    private static final String           LOCALBUILDVERSION = "localbuildversion";
    private static final String           IFNONEMATCH       = "If-None-Match";
    private static final String           IFMATCH           = "If-Match";
    private static final String           IFMODIFIEDSINCE   = "If-Modified-Since";
    private static final String           ANY               = "*";

    private static final Logger LOG = LogManager.getLogger(CacheUtils.class);

    private CacheUtils() {
        // empty constructor to prevent initialization
    }

    public static String encodeBase64ETag(String originalEtag, String manifestApiVersion, String separator, boolean weakETag) {
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
    public static String[] decodeBase64ETag(String eTag){
        String decoded = new String(Base64.getDecoder().decode(eTag));
        String separator = " ";
        if (StringUtils.containsAny(decoded, "|")){
            separator = "|";
        } else if (StringUtils.containsAny(decoded, ",")){
            separator = ",";
        }
        return StringUtils.splitByWholeSeparator(new String(Base64.getDecoder().decode(eTag)), separator);
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
     * New! Supports multiple values presented within If-None-Match / If-Match headers
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
                String[] decodedBase64ETag = decodeBase64ETag(spicAndSpan(value));
                if (StringUtils.equalsIgnoreCase(decodedBase64ETag[1], appVersion)){
                    return decodedBase64ETag[0];
                }
            }
        }
        return "";
    }

    public static String spicAndSpan(String header){
        return StringUtils.remove(StringUtils.stripStart(header, "W/"), "\"");
    }


    /**
     * Calculates SHA256 hash of a particular data string
     * @param  data String of data on which the hash is based
     * @return SHA256Hash   String
     */
    private static String getSHA256Hash(String data){
        MessageDigest digest = null;
        try {
            digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedhash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(encodedhash);
        } catch (NoSuchAlgorithmException e) {
            LogManager.getLogger(CacheUtils.class).error("Error generating SHA-265 hash from record timestamp_update", e);
        }
        return null;
    }

    private static String bytesToHex(byte[] hash) {
        StringBuilder hexString = new StringBuilder();
        for (byte bt : hash) {
            String hex = Integer.toHexString(0xff & bt);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

    public static String rePackage(String eTag){
        return "W/\"" + eTag + "\"";
    }




//    /**
//     * Generates an eTag surrounded with double quotes
//     * @param data
//     * @param weakETag if true then the eTag will start with W/
//     * @return
//     */
//    public static String generateSHA256ETag(String data, boolean weakETag) {
//        String eTag = "\"" + getSHA256Hash(data) + "\"";
//        if (weakETag) {
//            return "W/" + eTag;
//        }
//        return eTag;
//    }

//    /**
//     * Generates an eTag surrounded with double quotes
//     * @param data
//     * @param weakETag if true then the eTag will start with W/
//     * @return
//     */
//    public static String generateBase64ETag(String data, boolean weakETag) {
//        String eTag = "\"" + getSHA256Hash(data) + "\"";
//        if (weakETag) {
//            return "W/" + eTag;
//        }
//        return eTag;
//    }

//    /**
//     * Formats the given date according to the RFC 1123 pattern (e.g. Thu, 4 Oct 2018 10:34:20 GMT)
//     * @param lastModified
//     * @return
//     */
//    private static String headerDateToString(ZonedDateTime lastModified) {
//        return lastModified.format(DateTimeFormatter.RFC_1123_DATE_TIME);
//    }


//    /**
//     * Parses the given string into a ZonedDateTime object
//     * @param dateString
//     * @return ZonedDateTime
//     */
//    private static ZonedDateTime stringToZonedUTC(String dateString) {
//        if (StringUtils.isEmpty(dateString)) {
//            return null;
//        }
//        // Note that Apache DateUtils can parse all 3 date format patterns allowed by RFC 2616
//        Date date = DateUtils.parseDate(dateString);
//        if (date == null) {
//            LOG.error("Error parsing request header Date string: " + dateString);
//            return null;
//        }
//        return dateToZonedUTC(date);
//    }
//
//    /**
//     * Transforms a java.util.Date object to a ZonedDateTime object
//     * @param date input Date object
//     * @return ZonedDateTime representation of input date
//     */
//    private static ZonedDateTime dateToZonedUTC(Date date){
//        return date.toInstant().atOffset(ZoneOffset.UTC).toZonedDateTime().withNano(0);
//    }

//    /**
//     * Set the HTTP caching header values
//     * @param recordResponse required, RecordResponse object containing the Record API response
//     * @param eTag           optional, if not null then an ETag header is added
//     * @param tsUpdated      optional, if not null then a Last-Modified header is added
//     * @param allow          optional, if not null then an Allow header is added
//     * @param cacheControl   optional, if not null then a Cache-Control header is added
//     */
//    public static void addDefaultHeaders(RecordResponse recordResponse, String eTag,
//                                                 String tsUpdated, String allow, String cacheControl){
//        if (StringUtils.isNotBlank(eTag)) {
//            recordResponse.setETagHeader(eTag);
//        }
//        if (StringUtils.isNotBlank(tsUpdated)) {
//            recordResponse.setLastModifiedHeader(tsUpdated);
//        }
//        if (StringUtils.isNotBlank(allow)) {
//            recordResponse.setAllowHeader(allow);
//        }
//        if (StringUtils.isNotBlank(cacheControl)) {
//            recordResponse.setCacheControlHeader(cacheControl);
//        }
//    }

//    /**
//     * Set the CORS related header values
//     * @param response      required, RecordResponse object containing the Record API response
//     * @param allowMethods  optional, if not null then an Access-Control-Allow-Methods header is added
//     * @param allowHeaders  optional, if not null then an Access-Control-Allow-Headers header is added
//     * @param exposeHeaders optional, if not null then an Access-Control-Expose-Headers header is added
//     * @param maxAge        optional, if not null then an Access-Control-Max-Age header is added
//     */
//    public static void addCorsHeaders(RecordResponse recordResponse, String allowMethods,
//                                              String allowHeaders, String exposeHeaders, String maxAge){
//        if (StringUtils.isNotBlank(allowMethods)) {
//            recordResponse.setAcAllowMethodsHeader(allowMethods);
//        }
//        if (StringUtils.isNotBlank(allowHeaders)) {
//            recordResponse.setAcAllowHeadersHeader(allowHeaders);
//        }
//        if (StringUtils.isNotBlank(exposeHeaders)) {
//            recordResponse.setAcExposeHeadersHeader(exposeHeaders);
//        }
//        if (StringUtils.isNotBlank(maxAge)) {
//            recordResponse.setAcMaxAgeHeader(maxAge);
//        }
//    }


//    /**
//     * @param request incoming HttpServletRequest
//     * @param headers headers that should be sent back in the response
//     * @param lastModified ZonedDateTime that indicates the lastModified date of the requested data
//     * @param eTag String with the calculated eTag of the requested data
//     * @return ResponseEntity with 304 or 312 status if requested object has not changed, otherwise null
//     */
//    public static ResponseEntity checkCached(HttpServletRequest request, HttpHeaders headers,
//                                              ZonedDateTime lastModified, String eTag) {
//        // chosen this implementation instead of the 'shallow' out-of-the-box spring boot version because that does not
//        // offer the advantage of saving on processing time
//        ZonedDateTime requestLastModified = headerStringToDate(request.getHeader("If-Modified-Since"));
//        if((requestLastModified !=null && requestLastModified.compareTo(lastModified) > 0) ||
//                (StringUtils.isNotEmpty(request.getHeader("If-None-Match")) &&
//                        StringUtils.equalsIgnoreCase(request.getHeader("If-None-Match"), eTag))) {
//            // TODO Also we ignore possible multiple eTags for now
//            return new ResponseEntity<>(headers, HttpStatus.NOT_MODIFIED);
//        } else if (StringUtils.isNotEmpty(request.getHeader("If-Match")) &&
//                (!StringUtils.equalsIgnoreCase(request.getHeader("If-Match"), eTag) &&
//                        !StringUtils.equalsIgnoreCase(request.getHeader("If-Match"), "*"))) {
//            // Note that according to the specification we have to use strong ETags here (but for now we just ignore that)
//            // see https://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.24
//            // TODO Also we ignore possible multiple eTags for now
//            return new ResponseEntity<>(headers, HttpStatus.PRECONDITION_FAILED);
//        }
//        return null;
//    }
//
//    /**
//     * Parses the date string received in a request header
//     * @param dateString
//     * @return Date
//     */
//    private static ZonedDateTime headerStringToDate(String dateString) {
//        if (StringUtils.isEmpty(dateString)) {
//            return null;
//        }
//        // Note that Apache DateUtils can parse all 3 date format patterns allowed by RFC 2616
//        Date headerDate = DateUtils.parseDate(dateString);
//        if (headerDate == null) {
//            LogManager.getLogger(ManifestController.class).error("Error parsing request header Date string: {}", dateString);
//            return null;
//        }
//        return headerDate.toInstant().atOffset(ZoneOffset.UTC).toZonedDateTime();
//    }
}
