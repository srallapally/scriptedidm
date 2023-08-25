@Grapes([
        @Grab(group='org.codehaus.groovy.modules.http-builder', module='http-builder', version='0.7.1'),
        @Grab('com.github.groovy-wslite:groovy-wslite:1.1.3;transitive=false'),
        @Grab(group='org.slf4j', module='slf4j-api', version='1.6.1'),
        @Grab(group='ch.qos.logback', module='logback-classic', version='0.9.28')
])
import org.forgerock.openicf.connectors.groovy.OperationType
import org.forgerock.openicf.connectors.groovy.ScriptedConfiguration
import org.identityconnectors.common.logging.Log
import org.identityconnectors.framework.common.objects.ObjectClass
import org.identityconnectors.framework.common.objects.OperationOptions
import org.identityconnectors.framework.common.objects.filter.EqualsFilter
import org.identityconnectors.framework.common.objects.filter.Filter
import org.identityconnectors.framework.common.objects.filter.OrFilter
import wslite.rest.RESTClient

def operation = operation as OperationType
def configuration = configuration as ScriptedConfiguration
def filter = filter as Filter
def log = log as Log
def objectClass = objectClass as ObjectClass
def options = options as OperationOptions
def pageSize = 10
def currentPagedResultsCookie = null
def resultCount = 0

def openidm_user = null
def openidm_password = null
String IDMURL = 'http://localhost:8080/openidm'
RESTClient client = null
client = new RESTClient(IDMURL)
client.httpClient.sslTrustAllCerts = true

println "########## Entering " + operation + " Script"
println "########## ObjectClass: " + objectClass.objectClassValue
def query = [:]
def queryFilter = 'true'
def get = false

//query['_queryFilter'] = queryFilter

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
        //def path = "/managed/user?_sortKeys=userName&_fields=userName,givenName,sn,mail"
        def path = "/managed/user?_sortKeys=userName&_fields=*"
        query.each {key, value ->
            if(value){
                path = path + "&"+key+"="+value
            }
        }
        path = path + "&_totalPagedResultsPolicy=ESTIMATE"
        if(filter != null){
            def username = null
            if (filter instanceof EqualsFilter){
                //println "#### EqualsFilter ####"
                def attrName = ((EqualsFilter) filter).getAttribute()
                if (attrName.is(Uid.NAME) || attrName.is(Name.NAME)) {
                    username = ((EqualsFilter) filter).getAttribute().getValue().get(0)
                }
                path = path + "&_queryFilter=userName%20eq%20%22"+username+"%22"
                get = true
            } else if (filter instanceof OrFilter){
                //println "#### OrFilter ####"
                def keys = getOrFilters((OrFilter)filter)
               // println "#### keys ####" + keys
                def s = null
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
        //println "Query String: " + path
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
           def roleList = []
               //println "##########" + item.userName
               //println "Effective Roles: " + item.effectiveRoles
               item.effectiveRoles.each { role ->
                   //println "Role:" + role._refResourceId
                   roleList.add(role._refResourceId)
               }   
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
           println " Returning from script : "+ currentPagedResultsCookie + " and index = " + index
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
        println "Query String: " + path
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

/**
 *
 * @param epath
 * @param pageCookie
 * @return
 */
def getObjects(RESTClient client, String path, String pageCookie){
    println path
    if(pageCookie != null){
        path = path + "&_pagedResultsCookie=" + pageCookie
    }
    def response = client.get(path: path,
            headers: ['X-OpenIDM-Username': 'openidm-admin',
                      "X-OpenIDM-Password": 'openidm-admin',
                      "Accept-API-Version": "resource=1.0"])
    println response
    return response
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