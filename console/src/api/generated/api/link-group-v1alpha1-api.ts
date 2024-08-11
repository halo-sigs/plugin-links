/* tslint:disable */
/* eslint-disable */
/**
 * Halo
 * No description provided (generated by Openapi Generator https://github.com/openapitools/openapi-generator)
 *
 * The version of the OpenAPI document: 2.17.2
 * 
 *
 * NOTE: This class is auto generated by OpenAPI Generator (https://openapi-generator.tech).
 * https://openapi-generator.tech
 * Do not edit the class manually.
 */


import type { Configuration } from '../configuration';
import type { AxiosPromise, AxiosInstance, RawAxiosRequestConfig } from 'axios';
import globalAxios from 'axios';
// Some imports not used depending on template conditions
// @ts-ignore
import { DUMMY_BASE_URL, assertParamExists, setApiKeyToObject, setBasicAuthToObject, setBearerAuthToObject, setOAuthToObject, setSearchParams, serializeDataIfNeeded, toPathString, createRequestFunction } from '../common';
// @ts-ignore
import { BASE_PATH, COLLECTION_FORMATS, type RequestArgs, BaseAPI, RequiredError, operationServerMap } from '../base';
// @ts-ignore
import type { JsonPatchInner } from '../models';
// @ts-ignore
import type { LinkGroup } from '../models';
// @ts-ignore
import type { LinkGroupList } from '../models';
/**
 * LinkGroupV1alpha1Api - axios parameter creator
 * @export
 */
export const LinkGroupV1alpha1ApiAxiosParamCreator = function (configuration?: Configuration) {
    return {
        /**
         * Create LinkGroup
         * @param {LinkGroup} [linkGroup] Fresh linkgroup
         * @param {*} [options] Override http request option.
         * @throws {RequiredError}
         */
        createLinkGroup: async (linkGroup?: LinkGroup, options: RawAxiosRequestConfig = {}): Promise<RequestArgs> => {
            const localVarPath = `/apis/core.halo.run/v1alpha1/linkgroups`;
            // use dummy base URL string because the URL constructor only accepts absolute URLs.
            const localVarUrlObj = new URL(localVarPath, DUMMY_BASE_URL);
            let baseOptions;
            if (configuration) {
                baseOptions = configuration.baseOptions;
            }

            const localVarRequestOptions = { method: 'POST', ...baseOptions, ...options};
            const localVarHeaderParameter = {} as any;
            const localVarQueryParameter = {} as any;

            // authentication basicAuth required
            // http basic authentication required
            setBasicAuthToObject(localVarRequestOptions, configuration)

            // authentication bearerAuth required
            // http bearer authentication required
            await setBearerAuthToObject(localVarHeaderParameter, configuration)


    
            localVarHeaderParameter['Content-Type'] = 'application/json';

            setSearchParams(localVarUrlObj, localVarQueryParameter);
            let headersFromBaseOptions = baseOptions && baseOptions.headers ? baseOptions.headers : {};
            localVarRequestOptions.headers = {...localVarHeaderParameter, ...headersFromBaseOptions, ...options.headers};
            localVarRequestOptions.data = serializeDataIfNeeded(linkGroup, localVarRequestOptions, configuration)

            return {
                url: toPathString(localVarUrlObj),
                options: localVarRequestOptions,
            };
        },
        /**
         * Delete LinkGroup
         * @param {string} name Name of linkgroup
         * @param {*} [options] Override http request option.
         * @throws {RequiredError}
         */
        deleteLinkGroup: async (name: string, options: RawAxiosRequestConfig = {}): Promise<RequestArgs> => {
            // verify required parameter 'name' is not null or undefined
            assertParamExists('deleteLinkGroup', 'name', name)
            const localVarPath = `/apis/core.halo.run/v1alpha1/linkgroups/{name}`
                .replace(`{${"name"}}`, encodeURIComponent(String(name)));
            // use dummy base URL string because the URL constructor only accepts absolute URLs.
            const localVarUrlObj = new URL(localVarPath, DUMMY_BASE_URL);
            let baseOptions;
            if (configuration) {
                baseOptions = configuration.baseOptions;
            }

            const localVarRequestOptions = { method: 'DELETE', ...baseOptions, ...options};
            const localVarHeaderParameter = {} as any;
            const localVarQueryParameter = {} as any;

            // authentication basicAuth required
            // http basic authentication required
            setBasicAuthToObject(localVarRequestOptions, configuration)

            // authentication bearerAuth required
            // http bearer authentication required
            await setBearerAuthToObject(localVarHeaderParameter, configuration)


    
            setSearchParams(localVarUrlObj, localVarQueryParameter);
            let headersFromBaseOptions = baseOptions && baseOptions.headers ? baseOptions.headers : {};
            localVarRequestOptions.headers = {...localVarHeaderParameter, ...headersFromBaseOptions, ...options.headers};

            return {
                url: toPathString(localVarUrlObj),
                options: localVarRequestOptions,
            };
        },
        /**
         * Get LinkGroup
         * @param {string} name Name of linkgroup
         * @param {*} [options] Override http request option.
         * @throws {RequiredError}
         */
        getLinkGroup: async (name: string, options: RawAxiosRequestConfig = {}): Promise<RequestArgs> => {
            // verify required parameter 'name' is not null or undefined
            assertParamExists('getLinkGroup', 'name', name)
            const localVarPath = `/apis/core.halo.run/v1alpha1/linkgroups/{name}`
                .replace(`{${"name"}}`, encodeURIComponent(String(name)));
            // use dummy base URL string because the URL constructor only accepts absolute URLs.
            const localVarUrlObj = new URL(localVarPath, DUMMY_BASE_URL);
            let baseOptions;
            if (configuration) {
                baseOptions = configuration.baseOptions;
            }

            const localVarRequestOptions = { method: 'GET', ...baseOptions, ...options};
            const localVarHeaderParameter = {} as any;
            const localVarQueryParameter = {} as any;

            // authentication basicAuth required
            // http basic authentication required
            setBasicAuthToObject(localVarRequestOptions, configuration)

            // authentication bearerAuth required
            // http bearer authentication required
            await setBearerAuthToObject(localVarHeaderParameter, configuration)


    
            setSearchParams(localVarUrlObj, localVarQueryParameter);
            let headersFromBaseOptions = baseOptions && baseOptions.headers ? baseOptions.headers : {};
            localVarRequestOptions.headers = {...localVarHeaderParameter, ...headersFromBaseOptions, ...options.headers};

            return {
                url: toPathString(localVarUrlObj),
                options: localVarRequestOptions,
            };
        },
        /**
         * List LinkGroup
         * @param {number} [page] Page number. Default is 0.
         * @param {number} [size] Size number. Default is 0.
         * @param {Array<string>} [labelSelector] Label selector. e.g.: hidden!&#x3D;true
         * @param {Array<string>} [fieldSelector] Field selector. e.g.: metadata.name&#x3D;&#x3D;halo
         * @param {Array<string>} [sort] Sorting criteria in the format: property,(asc|desc). Default sort order is ascending. Multiple sort criteria are supported.
         * @param {*} [options] Override http request option.
         * @throws {RequiredError}
         */
        listLinkGroup: async (page?: number, size?: number, labelSelector?: Array<string>, fieldSelector?: Array<string>, sort?: Array<string>, options: RawAxiosRequestConfig = {}): Promise<RequestArgs> => {
            const localVarPath = `/apis/core.halo.run/v1alpha1/linkgroups`;
            // use dummy base URL string because the URL constructor only accepts absolute URLs.
            const localVarUrlObj = new URL(localVarPath, DUMMY_BASE_URL);
            let baseOptions;
            if (configuration) {
                baseOptions = configuration.baseOptions;
            }

            const localVarRequestOptions = { method: 'GET', ...baseOptions, ...options};
            const localVarHeaderParameter = {} as any;
            const localVarQueryParameter = {} as any;

            // authentication basicAuth required
            // http basic authentication required
            setBasicAuthToObject(localVarRequestOptions, configuration)

            // authentication bearerAuth required
            // http bearer authentication required
            await setBearerAuthToObject(localVarHeaderParameter, configuration)

            if (page !== undefined) {
                localVarQueryParameter['page'] = page;
            }

            if (size !== undefined) {
                localVarQueryParameter['size'] = size;
            }

            if (labelSelector) {
                localVarQueryParameter['labelSelector'] = labelSelector;
            }

            if (fieldSelector) {
                localVarQueryParameter['fieldSelector'] = fieldSelector;
            }

            if (sort) {
                localVarQueryParameter['sort'] = sort;
            }


    
            setSearchParams(localVarUrlObj, localVarQueryParameter);
            let headersFromBaseOptions = baseOptions && baseOptions.headers ? baseOptions.headers : {};
            localVarRequestOptions.headers = {...localVarHeaderParameter, ...headersFromBaseOptions, ...options.headers};

            return {
                url: toPathString(localVarUrlObj),
                options: localVarRequestOptions,
            };
        },
        /**
         * Patch LinkGroup
         * @param {string} name Name of linkgroup
         * @param {Array<JsonPatchInner>} [jsonPatchInner] 
         * @param {*} [options] Override http request option.
         * @throws {RequiredError}
         */
        patchLinkGroup: async (name: string, jsonPatchInner?: Array<JsonPatchInner>, options: RawAxiosRequestConfig = {}): Promise<RequestArgs> => {
            // verify required parameter 'name' is not null or undefined
            assertParamExists('patchLinkGroup', 'name', name)
            const localVarPath = `/apis/core.halo.run/v1alpha1/linkgroups/{name}`
                .replace(`{${"name"}}`, encodeURIComponent(String(name)));
            // use dummy base URL string because the URL constructor only accepts absolute URLs.
            const localVarUrlObj = new URL(localVarPath, DUMMY_BASE_URL);
            let baseOptions;
            if (configuration) {
                baseOptions = configuration.baseOptions;
            }

            const localVarRequestOptions = { method: 'PATCH', ...baseOptions, ...options};
            const localVarHeaderParameter = {} as any;
            const localVarQueryParameter = {} as any;

            // authentication basicAuth required
            // http basic authentication required
            setBasicAuthToObject(localVarRequestOptions, configuration)

            // authentication bearerAuth required
            // http bearer authentication required
            await setBearerAuthToObject(localVarHeaderParameter, configuration)


    
            localVarHeaderParameter['Content-Type'] = 'application/json-patch+json';

            setSearchParams(localVarUrlObj, localVarQueryParameter);
            let headersFromBaseOptions = baseOptions && baseOptions.headers ? baseOptions.headers : {};
            localVarRequestOptions.headers = {...localVarHeaderParameter, ...headersFromBaseOptions, ...options.headers};
            localVarRequestOptions.data = serializeDataIfNeeded(jsonPatchInner, localVarRequestOptions, configuration)

            return {
                url: toPathString(localVarUrlObj),
                options: localVarRequestOptions,
            };
        },
        /**
         * Update LinkGroup
         * @param {string} name Name of linkgroup
         * @param {LinkGroup} [linkGroup] Updated linkgroup
         * @param {*} [options] Override http request option.
         * @throws {RequiredError}
         */
        updateLinkGroup: async (name: string, linkGroup?: LinkGroup, options: RawAxiosRequestConfig = {}): Promise<RequestArgs> => {
            // verify required parameter 'name' is not null or undefined
            assertParamExists('updateLinkGroup', 'name', name)
            const localVarPath = `/apis/core.halo.run/v1alpha1/linkgroups/{name}`
                .replace(`{${"name"}}`, encodeURIComponent(String(name)));
            // use dummy base URL string because the URL constructor only accepts absolute URLs.
            const localVarUrlObj = new URL(localVarPath, DUMMY_BASE_URL);
            let baseOptions;
            if (configuration) {
                baseOptions = configuration.baseOptions;
            }

            const localVarRequestOptions = { method: 'PUT', ...baseOptions, ...options};
            const localVarHeaderParameter = {} as any;
            const localVarQueryParameter = {} as any;

            // authentication basicAuth required
            // http basic authentication required
            setBasicAuthToObject(localVarRequestOptions, configuration)

            // authentication bearerAuth required
            // http bearer authentication required
            await setBearerAuthToObject(localVarHeaderParameter, configuration)


    
            localVarHeaderParameter['Content-Type'] = 'application/json';

            setSearchParams(localVarUrlObj, localVarQueryParameter);
            let headersFromBaseOptions = baseOptions && baseOptions.headers ? baseOptions.headers : {};
            localVarRequestOptions.headers = {...localVarHeaderParameter, ...headersFromBaseOptions, ...options.headers};
            localVarRequestOptions.data = serializeDataIfNeeded(linkGroup, localVarRequestOptions, configuration)

            return {
                url: toPathString(localVarUrlObj),
                options: localVarRequestOptions,
            };
        },
    }
};

/**
 * LinkGroupV1alpha1Api - functional programming interface
 * @export
 */
export const LinkGroupV1alpha1ApiFp = function(configuration?: Configuration) {
    const localVarAxiosParamCreator = LinkGroupV1alpha1ApiAxiosParamCreator(configuration)
    return {
        /**
         * Create LinkGroup
         * @param {LinkGroup} [linkGroup] Fresh linkgroup
         * @param {*} [options] Override http request option.
         * @throws {RequiredError}
         */
        async createLinkGroup(linkGroup?: LinkGroup, options?: RawAxiosRequestConfig): Promise<(axios?: AxiosInstance, basePath?: string) => AxiosPromise<LinkGroup>> {
            const localVarAxiosArgs = await localVarAxiosParamCreator.createLinkGroup(linkGroup, options);
            const localVarOperationServerIndex = configuration?.serverIndex ?? 0;
            const localVarOperationServerBasePath = operationServerMap['LinkGroupV1alpha1Api.createLinkGroup']?.[localVarOperationServerIndex]?.url;
            return (axios, basePath) => createRequestFunction(localVarAxiosArgs, globalAxios, BASE_PATH, configuration)(axios, localVarOperationServerBasePath || basePath);
        },
        /**
         * Delete LinkGroup
         * @param {string} name Name of linkgroup
         * @param {*} [options] Override http request option.
         * @throws {RequiredError}
         */
        async deleteLinkGroup(name: string, options?: RawAxiosRequestConfig): Promise<(axios?: AxiosInstance, basePath?: string) => AxiosPromise<void>> {
            const localVarAxiosArgs = await localVarAxiosParamCreator.deleteLinkGroup(name, options);
            const localVarOperationServerIndex = configuration?.serverIndex ?? 0;
            const localVarOperationServerBasePath = operationServerMap['LinkGroupV1alpha1Api.deleteLinkGroup']?.[localVarOperationServerIndex]?.url;
            return (axios, basePath) => createRequestFunction(localVarAxiosArgs, globalAxios, BASE_PATH, configuration)(axios, localVarOperationServerBasePath || basePath);
        },
        /**
         * Get LinkGroup
         * @param {string} name Name of linkgroup
         * @param {*} [options] Override http request option.
         * @throws {RequiredError}
         */
        async getLinkGroup(name: string, options?: RawAxiosRequestConfig): Promise<(axios?: AxiosInstance, basePath?: string) => AxiosPromise<LinkGroup>> {
            const localVarAxiosArgs = await localVarAxiosParamCreator.getLinkGroup(name, options);
            const localVarOperationServerIndex = configuration?.serverIndex ?? 0;
            const localVarOperationServerBasePath = operationServerMap['LinkGroupV1alpha1Api.getLinkGroup']?.[localVarOperationServerIndex]?.url;
            return (axios, basePath) => createRequestFunction(localVarAxiosArgs, globalAxios, BASE_PATH, configuration)(axios, localVarOperationServerBasePath || basePath);
        },
        /**
         * List LinkGroup
         * @param {number} [page] Page number. Default is 0.
         * @param {number} [size] Size number. Default is 0.
         * @param {Array<string>} [labelSelector] Label selector. e.g.: hidden!&#x3D;true
         * @param {Array<string>} [fieldSelector] Field selector. e.g.: metadata.name&#x3D;&#x3D;halo
         * @param {Array<string>} [sort] Sorting criteria in the format: property,(asc|desc). Default sort order is ascending. Multiple sort criteria are supported.
         * @param {*} [options] Override http request option.
         * @throws {RequiredError}
         */
        async listLinkGroup(page?: number, size?: number, labelSelector?: Array<string>, fieldSelector?: Array<string>, sort?: Array<string>, options?: RawAxiosRequestConfig): Promise<(axios?: AxiosInstance, basePath?: string) => AxiosPromise<LinkGroupList>> {
            const localVarAxiosArgs = await localVarAxiosParamCreator.listLinkGroup(page, size, labelSelector, fieldSelector, sort, options);
            const localVarOperationServerIndex = configuration?.serverIndex ?? 0;
            const localVarOperationServerBasePath = operationServerMap['LinkGroupV1alpha1Api.listLinkGroup']?.[localVarOperationServerIndex]?.url;
            return (axios, basePath) => createRequestFunction(localVarAxiosArgs, globalAxios, BASE_PATH, configuration)(axios, localVarOperationServerBasePath || basePath);
        },
        /**
         * Patch LinkGroup
         * @param {string} name Name of linkgroup
         * @param {Array<JsonPatchInner>} [jsonPatchInner] 
         * @param {*} [options] Override http request option.
         * @throws {RequiredError}
         */
        async patchLinkGroup(name: string, jsonPatchInner?: Array<JsonPatchInner>, options?: RawAxiosRequestConfig): Promise<(axios?: AxiosInstance, basePath?: string) => AxiosPromise<LinkGroup>> {
            const localVarAxiosArgs = await localVarAxiosParamCreator.patchLinkGroup(name, jsonPatchInner, options);
            const localVarOperationServerIndex = configuration?.serverIndex ?? 0;
            const localVarOperationServerBasePath = operationServerMap['LinkGroupV1alpha1Api.patchLinkGroup']?.[localVarOperationServerIndex]?.url;
            return (axios, basePath) => createRequestFunction(localVarAxiosArgs, globalAxios, BASE_PATH, configuration)(axios, localVarOperationServerBasePath || basePath);
        },
        /**
         * Update LinkGroup
         * @param {string} name Name of linkgroup
         * @param {LinkGroup} [linkGroup] Updated linkgroup
         * @param {*} [options] Override http request option.
         * @throws {RequiredError}
         */
        async updateLinkGroup(name: string, linkGroup?: LinkGroup, options?: RawAxiosRequestConfig): Promise<(axios?: AxiosInstance, basePath?: string) => AxiosPromise<LinkGroup>> {
            const localVarAxiosArgs = await localVarAxiosParamCreator.updateLinkGroup(name, linkGroup, options);
            const localVarOperationServerIndex = configuration?.serverIndex ?? 0;
            const localVarOperationServerBasePath = operationServerMap['LinkGroupV1alpha1Api.updateLinkGroup']?.[localVarOperationServerIndex]?.url;
            return (axios, basePath) => createRequestFunction(localVarAxiosArgs, globalAxios, BASE_PATH, configuration)(axios, localVarOperationServerBasePath || basePath);
        },
    }
};

/**
 * LinkGroupV1alpha1Api - factory interface
 * @export
 */
export const LinkGroupV1alpha1ApiFactory = function (configuration?: Configuration, basePath?: string, axios?: AxiosInstance) {
    const localVarFp = LinkGroupV1alpha1ApiFp(configuration)
    return {
        /**
         * Create LinkGroup
         * @param {LinkGroupV1alpha1ApiCreateLinkGroupRequest} requestParameters Request parameters.
         * @param {*} [options] Override http request option.
         * @throws {RequiredError}
         */
        createLinkGroup(requestParameters: LinkGroupV1alpha1ApiCreateLinkGroupRequest = {}, options?: RawAxiosRequestConfig): AxiosPromise<LinkGroup> {
            return localVarFp.createLinkGroup(requestParameters.linkGroup, options).then((request) => request(axios, basePath));
        },
        /**
         * Delete LinkGroup
         * @param {LinkGroupV1alpha1ApiDeleteLinkGroupRequest} requestParameters Request parameters.
         * @param {*} [options] Override http request option.
         * @throws {RequiredError}
         */
        deleteLinkGroup(requestParameters: LinkGroupV1alpha1ApiDeleteLinkGroupRequest, options?: RawAxiosRequestConfig): AxiosPromise<void> {
            return localVarFp.deleteLinkGroup(requestParameters.name, options).then((request) => request(axios, basePath));
        },
        /**
         * Get LinkGroup
         * @param {LinkGroupV1alpha1ApiGetLinkGroupRequest} requestParameters Request parameters.
         * @param {*} [options] Override http request option.
         * @throws {RequiredError}
         */
        getLinkGroup(requestParameters: LinkGroupV1alpha1ApiGetLinkGroupRequest, options?: RawAxiosRequestConfig): AxiosPromise<LinkGroup> {
            return localVarFp.getLinkGroup(requestParameters.name, options).then((request) => request(axios, basePath));
        },
        /**
         * List LinkGroup
         * @param {LinkGroupV1alpha1ApiListLinkGroupRequest} requestParameters Request parameters.
         * @param {*} [options] Override http request option.
         * @throws {RequiredError}
         */
        listLinkGroup(requestParameters: LinkGroupV1alpha1ApiListLinkGroupRequest = {}, options?: RawAxiosRequestConfig): AxiosPromise<LinkGroupList> {
            return localVarFp.listLinkGroup(requestParameters.page, requestParameters.size, requestParameters.labelSelector, requestParameters.fieldSelector, requestParameters.sort, options).then((request) => request(axios, basePath));
        },
        /**
         * Patch LinkGroup
         * @param {LinkGroupV1alpha1ApiPatchLinkGroupRequest} requestParameters Request parameters.
         * @param {*} [options] Override http request option.
         * @throws {RequiredError}
         */
        patchLinkGroup(requestParameters: LinkGroupV1alpha1ApiPatchLinkGroupRequest, options?: RawAxiosRequestConfig): AxiosPromise<LinkGroup> {
            return localVarFp.patchLinkGroup(requestParameters.name, requestParameters.jsonPatchInner, options).then((request) => request(axios, basePath));
        },
        /**
         * Update LinkGroup
         * @param {LinkGroupV1alpha1ApiUpdateLinkGroupRequest} requestParameters Request parameters.
         * @param {*} [options] Override http request option.
         * @throws {RequiredError}
         */
        updateLinkGroup(requestParameters: LinkGroupV1alpha1ApiUpdateLinkGroupRequest, options?: RawAxiosRequestConfig): AxiosPromise<LinkGroup> {
            return localVarFp.updateLinkGroup(requestParameters.name, requestParameters.linkGroup, options).then((request) => request(axios, basePath));
        },
    };
};

/**
 * Request parameters for createLinkGroup operation in LinkGroupV1alpha1Api.
 * @export
 * @interface LinkGroupV1alpha1ApiCreateLinkGroupRequest
 */
export interface LinkGroupV1alpha1ApiCreateLinkGroupRequest {
    /**
     * Fresh linkgroup
     * @type {LinkGroup}
     * @memberof LinkGroupV1alpha1ApiCreateLinkGroup
     */
    readonly linkGroup?: LinkGroup
}

/**
 * Request parameters for deleteLinkGroup operation in LinkGroupV1alpha1Api.
 * @export
 * @interface LinkGroupV1alpha1ApiDeleteLinkGroupRequest
 */
export interface LinkGroupV1alpha1ApiDeleteLinkGroupRequest {
    /**
     * Name of linkgroup
     * @type {string}
     * @memberof LinkGroupV1alpha1ApiDeleteLinkGroup
     */
    readonly name: string
}

/**
 * Request parameters for getLinkGroup operation in LinkGroupV1alpha1Api.
 * @export
 * @interface LinkGroupV1alpha1ApiGetLinkGroupRequest
 */
export interface LinkGroupV1alpha1ApiGetLinkGroupRequest {
    /**
     * Name of linkgroup
     * @type {string}
     * @memberof LinkGroupV1alpha1ApiGetLinkGroup
     */
    readonly name: string
}

/**
 * Request parameters for listLinkGroup operation in LinkGroupV1alpha1Api.
 * @export
 * @interface LinkGroupV1alpha1ApiListLinkGroupRequest
 */
export interface LinkGroupV1alpha1ApiListLinkGroupRequest {
    /**
     * Page number. Default is 0.
     * @type {number}
     * @memberof LinkGroupV1alpha1ApiListLinkGroup
     */
    readonly page?: number

    /**
     * Size number. Default is 0.
     * @type {number}
     * @memberof LinkGroupV1alpha1ApiListLinkGroup
     */
    readonly size?: number

    /**
     * Label selector. e.g.: hidden!&#x3D;true
     * @type {Array<string>}
     * @memberof LinkGroupV1alpha1ApiListLinkGroup
     */
    readonly labelSelector?: Array<string>

    /**
     * Field selector. e.g.: metadata.name&#x3D;&#x3D;halo
     * @type {Array<string>}
     * @memberof LinkGroupV1alpha1ApiListLinkGroup
     */
    readonly fieldSelector?: Array<string>

    /**
     * Sorting criteria in the format: property,(asc|desc). Default sort order is ascending. Multiple sort criteria are supported.
     * @type {Array<string>}
     * @memberof LinkGroupV1alpha1ApiListLinkGroup
     */
    readonly sort?: Array<string>
}

/**
 * Request parameters for patchLinkGroup operation in LinkGroupV1alpha1Api.
 * @export
 * @interface LinkGroupV1alpha1ApiPatchLinkGroupRequest
 */
export interface LinkGroupV1alpha1ApiPatchLinkGroupRequest {
    /**
     * Name of linkgroup
     * @type {string}
     * @memberof LinkGroupV1alpha1ApiPatchLinkGroup
     */
    readonly name: string

    /**
     * 
     * @type {Array<JsonPatchInner>}
     * @memberof LinkGroupV1alpha1ApiPatchLinkGroup
     */
    readonly jsonPatchInner?: Array<JsonPatchInner>
}

/**
 * Request parameters for updateLinkGroup operation in LinkGroupV1alpha1Api.
 * @export
 * @interface LinkGroupV1alpha1ApiUpdateLinkGroupRequest
 */
export interface LinkGroupV1alpha1ApiUpdateLinkGroupRequest {
    /**
     * Name of linkgroup
     * @type {string}
     * @memberof LinkGroupV1alpha1ApiUpdateLinkGroup
     */
    readonly name: string

    /**
     * Updated linkgroup
     * @type {LinkGroup}
     * @memberof LinkGroupV1alpha1ApiUpdateLinkGroup
     */
    readonly linkGroup?: LinkGroup
}

/**
 * LinkGroupV1alpha1Api - object-oriented interface
 * @export
 * @class LinkGroupV1alpha1Api
 * @extends {BaseAPI}
 */
export class LinkGroupV1alpha1Api extends BaseAPI {
    /**
     * Create LinkGroup
     * @param {LinkGroupV1alpha1ApiCreateLinkGroupRequest} requestParameters Request parameters.
     * @param {*} [options] Override http request option.
     * @throws {RequiredError}
     * @memberof LinkGroupV1alpha1Api
     */
    public createLinkGroup(requestParameters: LinkGroupV1alpha1ApiCreateLinkGroupRequest = {}, options?: RawAxiosRequestConfig) {
        return LinkGroupV1alpha1ApiFp(this.configuration).createLinkGroup(requestParameters.linkGroup, options).then((request) => request(this.axios, this.basePath));
    }

    /**
     * Delete LinkGroup
     * @param {LinkGroupV1alpha1ApiDeleteLinkGroupRequest} requestParameters Request parameters.
     * @param {*} [options] Override http request option.
     * @throws {RequiredError}
     * @memberof LinkGroupV1alpha1Api
     */
    public deleteLinkGroup(requestParameters: LinkGroupV1alpha1ApiDeleteLinkGroupRequest, options?: RawAxiosRequestConfig) {
        return LinkGroupV1alpha1ApiFp(this.configuration).deleteLinkGroup(requestParameters.name, options).then((request) => request(this.axios, this.basePath));
    }

    /**
     * Get LinkGroup
     * @param {LinkGroupV1alpha1ApiGetLinkGroupRequest} requestParameters Request parameters.
     * @param {*} [options] Override http request option.
     * @throws {RequiredError}
     * @memberof LinkGroupV1alpha1Api
     */
    public getLinkGroup(requestParameters: LinkGroupV1alpha1ApiGetLinkGroupRequest, options?: RawAxiosRequestConfig) {
        return LinkGroupV1alpha1ApiFp(this.configuration).getLinkGroup(requestParameters.name, options).then((request) => request(this.axios, this.basePath));
    }

    /**
     * List LinkGroup
     * @param {LinkGroupV1alpha1ApiListLinkGroupRequest} requestParameters Request parameters.
     * @param {*} [options] Override http request option.
     * @throws {RequiredError}
     * @memberof LinkGroupV1alpha1Api
     */
    public listLinkGroup(requestParameters: LinkGroupV1alpha1ApiListLinkGroupRequest = {}, options?: RawAxiosRequestConfig) {
        return LinkGroupV1alpha1ApiFp(this.configuration).listLinkGroup(requestParameters.page, requestParameters.size, requestParameters.labelSelector, requestParameters.fieldSelector, requestParameters.sort, options).then((request) => request(this.axios, this.basePath));
    }

    /**
     * Patch LinkGroup
     * @param {LinkGroupV1alpha1ApiPatchLinkGroupRequest} requestParameters Request parameters.
     * @param {*} [options] Override http request option.
     * @throws {RequiredError}
     * @memberof LinkGroupV1alpha1Api
     */
    public patchLinkGroup(requestParameters: LinkGroupV1alpha1ApiPatchLinkGroupRequest, options?: RawAxiosRequestConfig) {
        return LinkGroupV1alpha1ApiFp(this.configuration).patchLinkGroup(requestParameters.name, requestParameters.jsonPatchInner, options).then((request) => request(this.axios, this.basePath));
    }

    /**
     * Update LinkGroup
     * @param {LinkGroupV1alpha1ApiUpdateLinkGroupRequest} requestParameters Request parameters.
     * @param {*} [options] Override http request option.
     * @throws {RequiredError}
     * @memberof LinkGroupV1alpha1Api
     */
    public updateLinkGroup(requestParameters: LinkGroupV1alpha1ApiUpdateLinkGroupRequest, options?: RawAxiosRequestConfig) {
        return LinkGroupV1alpha1ApiFp(this.configuration).updateLinkGroup(requestParameters.name, requestParameters.linkGroup, options).then((request) => request(this.axios, this.basePath));
    }
}
