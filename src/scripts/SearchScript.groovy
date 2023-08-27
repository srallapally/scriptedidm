@Grapes([
        @Grab(group='org.codehaus.groovy.modules.http-builder', module='http-builder', version='0.7.1'),
        @Grab('com.github.groovy-wslite:groovy-wslite:1.1.3;transitive=false'),
        @Grab(group='org.slf4j', module='slf4j-api', version='1.6.1'),
        @Grab(group='ch.qos.logback', module='logback-classic', version='0.9.28')
])
import net.sf.json.JSONNull
import net.sf.json.groovy.JsonSlurper

import org.slf4j.*
import wslite.rest.ContentType
import wslite.rest.RESTClient

import org.forgerock.openicf.connectors.groovy.OperationType
import org.forgerock.openicf.connectors.groovy.ScriptedConfiguration
import org.identityconnectors.common.logging.Log
import org.identityconnectors.framework.common.objects.ObjectClass
import org.identityconnectors.framework.common.objects.OperationOptions
import org.forgerock.openicf.connectors.groovy.MapFilterVisitor
import org.identityconnectors.framework.common.objects.filter.Filter
import org.identityconnectors.framework.common.objects.filter.EqualsFilter
import org.identityconnectors.framework.common.objects.filter.OrFilter
import org.identityconnectors.framework.common.objects.filter.FilterBuilder
import org.identityconnectors.framework.common.objects.filter.StartsWithFilter
import org.identityconnectors.framework.common.exceptions.ConnectorException
import org.identityconnectors.framework.common.FrameworkUtil


def operation = operation as OperationType
def configuration = configuration as ScriptedConfiguration
def filter = filter as Filter
def log = log as Log
def objectClass = objectClass as ObjectClass
def options = options as OperationOptions
def pageSize = 10
def currentPagedResultsCookie = null
def resultCount = 0

/**
 *  TODO: Move IDM Credentials to Configuration Property Bag
 *  TODO: Move IDM URL to Configuration Property Bag
 */
def OPENIDM_USER = null
def OPENIDM_PASSWORD = null
String IDMURL = 'http://localhost:8080/openidm'


RESTClient client = null
client = new RESTClient(IDMURL)
client.httpClient.sslTrustAllCerts = true

println "########## Entering " + operation + " Script"
println "########## ObjectClass: " + objectClass.objectClassValue
def query = [:]
def queryFilter = 'true'
def get = false


if (null != options.pageSize) {
    query['_pageSize'] = options.pageSize
    pageSize = options.pageSize
    if (null != options.pagedResultsCookie) {
        query['_pagedResultsCookie'] = options.pagedResultsCookie
        currentPagedResultsCookie = options.pagedResultsCookie.toString()
    }
    if (null != options.pagedResultsOffset) {
        query['_pagedResultsOffset'] = options.pagedResultsOffset
    }
}
switch (objectClass) {
    case objectClass.ACCOUNT:
        /**
         * TODO: Move All IDM URLs to another script
         */
        def path = "/managed/user?_sortKeys=userName&_fields=*"
        // Loop through the query and add the query parameters to the path
        query.each {key, value ->
            if(value){
                path = path + "&"+key+"="+value
            }
        }
        // I am not sure if totalPagedResultsPolicy actually works but following docs
        path = path + "&_totalPagedResultsPolicy=ESTIMATE"

        /**
         * Per the IDM Gurus, connector must implement Equals and Or Filters, especially
         * if we want the recon UI to work
         */
        if(filter != null){
            def username = null
            if (filter instanceof EqualsFilter){
                def attrName = ((EqualsFilter) filter).getAttribute()
                // In this specific connector, uid and id are the same so we can use either
                // See __GROUP__, where uid and id are different, for an example of how to
                // handle the attributes separately
                if (attrName.is(Uid.NAME) || attrName.is(Name.NAME)) {
                    username = ((EqualsFilter) filter).getAttribute().getValue().get(0)
                }
                path = path + "&_queryFilter=userName%20eq%20%22"+username+"%22"
                get = true
            } else if (filter instanceof OrFilter){
                // IDM issues an or query which looks like this:
                // __UID__ eq "bjensen" or __UID__ eq "jdoe"....
               // getOrFilters is a helper method that will return a list of search values
                def keys = getOrFilters((OrFilter)filter)

                def s = null
                // Loop through the list of search values and build the query string
                keys.each { key ->
                    if(s) {
                        s = s + "or%20userName%20eq%20%22"+key+"%22%20"
                    } else {
                         s = "userName%20eq%20%22"+key+"%22%20"
                    }    
                }
                path = path + "&_queryFilter="+s
                get = false
            }
        }
        else {
            path = path + "&_queryFilter=true"
        }        

        def resources = null
        def index = -1

        // At this point, we will either fetch 1 or a list
      
        def response = client.get(path: path,
                  headers: ['X-OpenIDM-Username': OPENIDM_USER,
                          "X-OpenIDM-Password": OPENIDM_PASSWORD,
                          "Accept-API-Version": "resource=1.0"])
        /**
         * We need to check the resultCount to see if we need to page through the results
         * and also to handle the last page of results
         */
        resultCount = response.json.resultCount.toInteger()  
        if(resultCount >= pageSize ){
              currentPagedResultsCookie = response.json.pagedResultsCookie.toString()
              index = response.json.remainingPagedResults

        } else {
             // in the last page of results, we need to reset the cookie and index
              currentPagedResultsCookie = null
              index = -1
        }
        // either we have a list of results or we are getting a single result
        // Not sure if we need this condition
        if( (response.json.pagedResultsCookie != null && response.json.resultCount.toInteger() > 0) ||
            get == true) {
           response.json.result.each { item ->
               def roleList = []
                roleList = getUserRoleIds(item.userName)   
                handler {
                  uid item.userName
                  id item.userName
                  attribute 'userName', item.userName
                  attribute 'givenName', item.givenName
                  attribute 'sn', item.sn
                  attribute 'mail', item.mail
                  attribute 'roles', roleList
               }
           }
        }
        return new SearchResult(currentPagedResultsCookie, index)
        break
    case objectClass.GROUP:
       //def path = "roletype%20eq%20%22Entitlement%22&fields=*"
        def path = "/managed/role?_sortKeys=name&field=*"
        //&_queryFilter=roletype%20eq%20%22Entitlement%22&_fields=*"
        query.each {key, value ->
            if(value){
                path = path + "&"+key+"="+value
            }
        }
        path = path + "&_totalPagedResultsPolicy=ESTIMATE"
        if(filter != null){
            def rolename = null
            if (filter instanceof EqualsFilter){
                //println "#### EqualsFilter ####"
                def attrName = ((EqualsFilter) filter).getAttribute()
                println "attrName: " + attrName
                if (attrName.is(Name.NAME)) {
                    rolename = ((EqualsFilter) filter).getAttribute().getValue().get(0)
                    rolename = java.net.URLEncoder.encode(rolename, "UTF-8") 
                    path = path + "&_queryFilter=roletype%20eq%20%22Entitlement%22%20and%20name%20eq%20%22"+rolename+"%22"
                } else if(attrName.is(Uid.NAME)){
                    rolename = ((EqualsFilter) filter).getAttribute().getValue().get(0)
                    rolename = java.net.URLEncoder.encode(rolename, "UTF-8") 
                    path = path + "&_queryFilter=roletype%20eq%20%22Entitlement%22%20and%20_id%20eq%20%22"+rolename+"%22"
                }
                get = true
            } else if (filter instanceof OrFilter){
                //println "#### OrFilter ####"
                def keys = getOrFilters((OrFilter)filter)
               // println "#### keys ####" + keys
                def s = null
                keys.each { key ->
                    if(s) {
                        s = s + "or%20nameame%20eq%20%22"+key+"%22%20"
                    } else {
                         s = "name%20eq%20%22"+key+"%22%20"
                    }    
                }
                path = path + "&_queryFilter=roletype%20eq%20%22Entitlement%22%20and%20%28"+s+"%29"
                get = false
            }
        }
        else {
            path = path + "&_queryFilter=true"
        }        
        println "Group Query String: " + path
        def resources = null
        def index = -1
      
        def response = client.get(path: path,
                  headers: ['X-OpenIDM-Username': 'openidm-admin',
                          "X-OpenIDM-Password": 'openidm-admin',
                          "Accept-API-Version": "resource=1.0"])

        resultCount = response.json.resultCount.toInteger()  
        if(resultCount >= pageSize ){
              currentPagedResultsCookie = response.json.pagedResultsCookie.toString()
              index = response.json.remainingPagedResults

        } else {
              currentPagedResultsCookie = null
              index = -1
        }
        if( (response.json.pagedResultsCookie != null && response.json.resultCount.toInteger() > 0) ||
            get == true) {
           //println "Returning search results "
           response.json.result.each { item ->
                handler {
                  uid item._id
                  id item.name
                  attribute 'roleName', item.name
                  attribute 'roleDescription', item.description
                  attribute 'appName', item.appName
               }
           }
        }
        return new SearchResult(currentPagedResultsCookie, index)                         
        break
    default:
        break
}

def getOrFilters(OrFilter filter) {
    def ids = []
    Filter left = filter.getLeft()
    Filter right = filter.getRight()
    if(left instanceof EqualsFilter) {
        String id = ((EqualsFilter)left).getAttribute().getValue().get(0).toString()
        ids.add(id)
    } else if(left instanceof OrFilter) {
        ids.addAll(getOrFilters((OrFilter)left))
    }
    if(right instanceof EqualsFilter) {
        String id = ((EqualsFilter)right).getAttribute().getValue().get(0).toString()
        ids.add(id)
    } else if(right instanceof OrFilter) {
        ids.addAll(getOrFilters((OrFilter)right))
    }
    return ids

}
def getUserId(String userName){
    RESTClient client = null
    String IDMURL = 'http://localhost:8080/openidm'
    client = new RESTClient(IDMURL)
    client.httpClient.sslTrustAllCerts = true
    def path = "/managed/user?_sortKeys=userName&_fields=*" + "&_queryFilter=userName+eq+%22" + userName + "%22"
    def response = client.get(path: path,
            headers: ['X-OpenIDM-Username': 'openidm-admin',
                      "X-OpenIDM-Password": 'openidm-admin',
                      "Accept-API-Version": "resource=1.0"])
    def roleList = []
    def userid = null
    response.json.result.each { item ->
        userid = item._id
    }
    println "userid: " + userid
    return userid
}

def getUserRoleIds(String userName){
    RESTClient client = null
    String IDMURL = 'http://localhost:8080/openidm'
    client = new RESTClient(IDMURL)
    client.httpClient.sslTrustAllCerts = true
    def roleIds = []
    def userid = null
    println " Getting user "+userName+" user id"
    userid = getUserId(userName)
    if(userid) {
        def path = "managed/user/"+userid+"/roles?_queryFilter=true&_fields=_ref/*,name"
        println "getUserRoleIds:"+path
        def response = client.get(path: path,
            headers: ['X-OpenIDM-Username': 'openidm-admin',
                      "X-OpenIDM-Password": 'openidm-admin',
                      "Accept-API-Version": "resource=1.0"])
    
        response.json.result.each { item ->
        //println "Adding "+item._id + " with name " + item.name
         roleIds.add(item._refResourceId)
        }
        return roleIds
    } else {
        println "User not found "+userName
        return null
    }
}