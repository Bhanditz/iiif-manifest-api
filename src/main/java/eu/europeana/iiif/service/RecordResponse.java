/*
 * Copyright 2007-2018 The Europeana Foundation
 *
 *  Licenced under the EUPL, Version 1.1 (the "Licence") and subsequent versions as approved
 *  by the European Commission;
 *  You may not use this work except in compliance with the Licence.
 *
 *  You may obtain a copy of the Licence at:
 *  http://joinup.ec.europa.eu/software/page/eupl
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under
 *  the Licence is distributed on an "AS IS" basis, without warranties or conditions of
 *  any kind, either express or implied.
 *  See the Licence for the specific language governing permissions and limitations under
 *  the Licence.
 */

package eu.europeana.iiif.service;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.Header;
import org.apache.http.client.methods.CloseableHttpResponse;

/**
 * Created by luthien on 22/10/2018.
 */
public class RecordResponse {
    protected String    json;
    private int         httpStatus;
    private String      lastModifiedHeader;
    private String      eTagHeader;
    private String      allowHeader;
    private String      cacheControlHeader;
    private String      acAllowMethodsHeader;
    private String      acAllowHeadersHeader;
    private String      acExposeHeadersHeader;
    private String      acMaxAgeHeader;
    private boolean     isIfNoneMatchRequest = false;


    public RecordResponse(int httpStatus, String lastModifiedHeader, String eTagHeader) {
        this.httpStatus = httpStatus;
        this.lastModifiedHeader = lastModifiedHeader;
        this.eTagHeader = eTagHeader;
    }

    public RecordResponse(CloseableHttpResponse response, boolean isIfNoneMatchRequest) {
        this.isIfNoneMatchRequest = isIfNoneMatchRequest;
        this.httpStatus = response.getStatusLine().getStatusCode();
        for (Header header : response.getAllHeaders()){
            switch (header.getName().toLowerCase()){
                case "last-modified":
                    if (StringUtils.isNotBlank(header.getValue()) && StringUtils.isBlank(this.lastModifiedHeader)){
                        this.lastModifiedHeader = header.getValue();
                    }
                    break;
                case "etag":
                    if (StringUtils.isNotBlank(header.getValue()) && StringUtils.isBlank(this.eTagHeader)){
                        this.eTagHeader = header.getValue();
                    }
                    break;
                case "cache-control":
                    if (StringUtils.isNotBlank(header.getValue()) && StringUtils.isBlank(this.cacheControlHeader)){
                        this.cacheControlHeader = header.getValue();
                    }
                    break;
                case "access-control-allow-methods":
                    if (StringUtils.isNotBlank(header.getValue()) && StringUtils.isBlank(this.acAllowMethodsHeader)){
                        this.acAllowMethodsHeader = header.getValue();
                    }
                    break;
                case "access-control-allow-headers":
                    if (StringUtils.isNotBlank(header.getValue()) && StringUtils.isBlank(this.acAllowHeadersHeader)){
                        this.acAllowHeadersHeader = header.getValue();
                    }
                    break;
                case "access-control-expose-headers":
                    if (StringUtils.isNotBlank(header.getValue()) && StringUtils.isBlank(this.acExposeHeadersHeader)){
                        this.acExposeHeadersHeader = header.getValue();
                    }
                    break;
                case "access-control-max-age":
                    if (StringUtils.isNotBlank(header.getValue()) && StringUtils.isBlank(this.acMaxAgeHeader)){
                        this.acMaxAgeHeader = header.getValue();
                    }
                    break;
                default: break;
            }
        }
    }

    public RecordResponse(String json) {
        this.json = json;
    }

    public RecordResponse(int httpStatus) {
        this.httpStatus = httpStatus;
    }

    public String getJson() {
        return json;
    }

    public void setJson(String json) {
        this.json = json;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public void setHttpStatus(int httpStatus) {
        this.httpStatus = httpStatus;
    }

    public String getLastModifiedHeader() {
        return lastModifiedHeader;
    }

    public void setLastModifiedHeader(String lastModifiedHeader) {
        this.lastModifiedHeader = lastModifiedHeader;
    }

    public String getETagHeader() {
        return eTagHeader;
    }

    public void setETagHeader(String eTagHeader) {
        this.eTagHeader = eTagHeader;
    }

    public String getAllowHeader() {
        return allowHeader;
    }

    public void setAllowHeader(String allowHeader) {
        this.allowHeader = allowHeader;
    }

    public String getCacheControlHeader() {
        return cacheControlHeader;
    }

    public void setCacheControlHeader(String cacheControlHeader) {
        this.cacheControlHeader = cacheControlHeader;
    }

    public String getAcAllowMethodsHeader() {
        return acAllowMethodsHeader;
    }

    public void setAcAllowMethodsHeader(String acAllowMethodsHeader) {
        this.acAllowMethodsHeader = acAllowMethodsHeader;
    }

    public String getAcAllowHeadersHeader() {
        return acAllowHeadersHeader;
    }

    public void setAcAllowHeadersHeader(String acAllowHeadersHeader) {
        this.acAllowHeadersHeader = acAllowHeadersHeader;
    }

    public String getAcExposeHeadersHeader() {
        return acExposeHeadersHeader;
    }

    public void setAcExposeHeadersHeader(String acExposeHeadersHeader) {
        this.acExposeHeadersHeader = acExposeHeadersHeader;
    }

    public String getAcMaxAgeHeader() {
        return acMaxAgeHeader;
    }

    public void setAcMaxAgeHeader(String acMaxAgeHeader) {
        this.acMaxAgeHeader = acMaxAgeHeader;
    }

    public boolean isIfNoneMatchRequest() {
        return isIfNoneMatchRequest;
    }
}
