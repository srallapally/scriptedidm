import groovy.transform.Field
@Grapes([
        @Grab(group='org.codehaus.groovy.modules.http-builder', module='http-builder', version='0.7.1'),
        @Grab('com.github.groovy-wslite:groovy-wslite:1.1.3;transitive=false'),
        @Grab(group='org.slf4j', module='slf4j-api', version='1.6.1'),
        @Grab(group='ch.qos.logback', module='logback-classic', version='0.9.28'),
        @Grab(group='org.apache.httpcomponents', module='httpclient', version='4.5.13')
])
import org.apache.http.client.methods.CloseableHttpResponse
import org.apache.http.client.methods.HttpPatch
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClients
import org.apache.http.util.EntityUtils
import org.forgerock.openicf.connectors.groovy.OperationType
import org.forgerock.openicf.connectors.groovy.ScriptedConfiguration
import org.identityconnectors.common.logging.Log
import org.identityconnectors.framework.common.exceptions.ConnectorException
import org.identityconnectors.framework.common.objects.*
import wslite.rest.RESTClient

def operation = operation as OperationType
def configuration = configuration as ScriptedConfiguration

def objectClass = objectClass as ObjectClass
def options = options as OperationOptions
def updateAttributes = new AttributesAccessor(attributes as Set<Attribute>)
def uid = uid as Uid
def log = log as Log

/**
 *  TODO: Move IDM Credentials to Configuration Property Bag
 *  TODO: Move IDM URL to Configuration Property Bag
 */
@Field final OPENIDM_USER = null
@Field final OPENIDM_PASSWORD = null
@Field final IDMURL = 'http://localhost:8080/openidm'

RESTClient client = null
client = new RESTClient(IDMURL)
client.httpClient.sslTrustAllCerts = true

println "Entering " + operation + " Script"
println "ObjectClass: " + objectClass.objectClassValue


switch(objectClass){
    case ObjectClass.ACCOUNT:
                /**
                 * IDM sends only the changed attributes in the update request
                 * Also, in case of multi-valued attributes, it sends the final state
                 */
                println "Updating Account for " + objectClass + ": " + uid.uidValue + " Account"
                def userName = null
                def firstName = null
                def lastName = null
                def currentRoles = []

                /** TODO: Make code changes here to handle all account attributes
                 *
                 */
                if (updateAttributes.hasAttribute("userName")) {
                    userName = updateAttributes.findString("userName")
                }

                if (updateAttributes.hasAttribute("sn")) {
                    firstName = updateAttributes.findString("sn")
                }

                if (updateAttributes.hasAttribute("givenName")) {
                    lastName = updateAttributes.findString("giveName")
                }
                /**
                 * Approach: Get the current state of the roles from IDM
                 * Compare the final state (sent by Cloud) with the current state
                 */
                if (updateAttributes.hasAttribute("roles")) {
                    currentRoles = updateAttributes.findList("roles")
                    log.ok(" Final Roles: " + currentRoles + "for " + uid.uidValue)
                    // To get current role memberships from IDM, we need the user id
                    def userid = getUserId(uid.uidValue)
                    def userRoles = []
                    userRoles = getUserRoles(uid.uidValue)
                    log.ok("Roles in IDM: " + userRoles)
                    // Roles to be revoked
                    if(userRoles.size() > 0){
                        userRoles.each {item ->
                            if(!currentRoles.contains(item)){
                                log.ok("Revoking role: " + item)
                                revokeRoleFromUser(userid,item as String)
                            }
                        }
                    }
                    // Roles to be added
                    if(currentRoles.size() > 0){
                        currentRoles.each {item ->
                            if(!userRoles.contains(item)){
                                log.ok("Adding role: " + item)
                                addRoleToUser(userid,item as String)
                            }
                        }
                    }
                }
                return userName
               break 
    case ObjectClass.GROUP:
                /**
                 * We aren't doing anything with groups in this example
                 */
                println "Entering update script for " + objectClass + " with attributes: " + updateAttributes + " Group"
                def groupName = null
                def groupDescription = null

                if (updateAttributes.hasAttribute("roleName")) {
                    groupName = updateAttributes.findString("roleName")
                }

                if (updateAttributes.hasAttribute("roleDescription")) {
                    groupDescription = updateAttributes.findString("roleDescription")
                }
                return groupName
    default:
                println "UpdateScript can not handle object type: " + objectClass.objectClassValue
                throw new ConnectorException("UpdateScript can not handle object type: " + objectClass.objectClassValue)
}


def getUserId(String userName){
    RESTClient client = null
    client = new RESTClient(IDMURL)
    client.httpClient.sslTrustAllCerts = true
    def path = "/managed/user?_sortKeys=userName&_fields=*" + "&_queryFilter=userName+eq+%22" + userName + "%22"
    def response = client.get(path: path,
            headers: ['X-OpenIDM-Username': OPENIDM_USER,
                      "X-OpenIDM-Password": OPENIDM_PASSWORD,
                      "Accept-API-Version": "resource=1.0"])
    def roleList = []
    def userid = null
    response.json.result.each { item ->
        userid = item._id
    }
    println "userid: " + userid
    return userid
}

def getUserRoles(String userid){
    RESTClient client = null
    client = new RESTClient(IDMURL)
    client.httpClient.sslTrustAllCerts = true
    def path = "/managed/user?_sortKeys=userName&_fields=*" + "&_queryFilter=userName+eq+%22" + userid + "%22"
    def response = client.get(path: path,
                  headers: ['X-OpenIDM-Username': OPENIDM_USER,
                          "X-OpenIDM-Password": OPENIDM_PASSWORD,
                          "Accept-API-Version": "resource=1.0"])
    def roleList = []                      
    response.json.result.each { item ->
            item.effectiveRoles.each { role ->
                roleList.add(role._refResourceId)
            }
    }        
    if(roleList.size() == 0){
        println "No roles found for user: " + userid
    }
    return roleList

}
/**
 *  IDM APIs call for the client to issue a PATCH request and I couldn't find any groovy-wslite way to
 *  do this. So, I am using Apache HTTP Client to issue the PATCH request
 *
 */
def addRoleToUser(String userId,String roleId){
    def grantString = "[{\"operation\": \"add\",\"field\": \"/roles/-\",\"value\": {\"_ref\" : \"managed/role/"+roleId+"\"}}]"
    log.ok("This is my grant string: " + grantString)
    apacheHttpRoleGrant(userId, grantString)
}

def revokeRoleFromUser(String userId, String roleId){
    RESTClient client = null

    client = new RESTClient(IDMURL)
    client.httpClient.sslTrustAllCerts = true
    //def userId = getUserId(userName)
    def path = "/managed/user/"+userId+"/roles?_queryFilter=_refResourceId%20eq%20%22"+roleId+"%22&_fields=_ref/*,name"
    def response = client.get(path: path,
            headers: ['X-OpenIDM-Username': OPENIDM_USER,
                      "X-OpenIDM-Password": OPENIDM_PASSWORD,
                      "Accept-API-Version": "resource=1.0"])
    def id = response.json.result[0]._id
    def refResId = response.json.result[0]._refResourceId
    def rev = response.json.result[0]._rev
    def ref = response.json.result[0]._ref
    def refResourceRev = response.json.result[0]._refResourceRev
    def revokeString = "[ { \"operation\":  \"remove\", \"field\": \"/roles\", " +
            "\"value\": { \"_ref\": \""+ref+"\"," +
            " \"_refResourceCollection\":  \"managed/role\", " +
            " \"_refResourceId\": \""+ refResId+"\", " +
            " \"_refProperties\":  { " +
            "\"_id\": \"" + id +"\", " +
            "\"_rev\": \""+ rev +"\" } } }]";
    println "This is my revoke string: " + revokeString
    apacheHttpRoleRevoke(userId, revokeString)
}

def apacheHttpRoleGrant(String userId, String grantString){
    def roleGrantStr = IDMURL + "/managed/user/"+userId
    println "This is my role grant string: " + grantString
    CloseableHttpClient httpClient2 =  HttpClients.createDefault();
    HttpPatch httpPatch2 = new HttpPatch(roleGrantStr);
    httpPatch2.setHeader("X-OpenIDM-Username",OPENIDM_USER);
    httpPatch2.setHeader("X-OpenIDM-Password", OPENIDM_PASSWORD);
    httpPatch2.setHeader("Accept-API-Version", "resource=1.0");
    httpPatch2.setHeader("Content-Type", "application/json");
    StringEntity entity2 = new StringEntity(grantString);
    //entity.setContentType();
    httpPatch2.setEntity(entity2);
    CloseableHttpResponse httpResponse2 = httpClient2.execute(httpPatch2);
    String msg = EntityUtils.toString(httpResponse2.getEntity());
    println "Response Code : " + httpResponse2.getStatusLine().getStatusCode();
}

def apacheHttpRoleRevoke(String userId, String revokeString){
    def roleDeleteStr = IDMURL + "/managed/user/"+userId
    println "This is my role delete string: " + roleDeleteStr
    CloseableHttpClient httpClient3 =  HttpClients.createDefault();
    HttpPatch httpPatch3 = new HttpPatch(roleDeleteStr);
    httpPatch3.setHeader("X-OpenIDM-Username",OPENIDM_USER);
    httpPatch3.setHeader("X-OpenIDM-Password", OPENIDM_PASSWORD);
    httpPatch3.setHeader("Accept-API-Version", "resource=1.0");
    httpPatch3.setHeader("Content-Type", "application/json");
    StringEntity entity3 = new StringEntity(revokeString);
    //entity.setContentType();
    httpPatch3.setEntity(entity3);
    CloseableHttpResponse httpResponse3 = httpClient3.execute(httpPatch3);
    String msg = EntityUtils.toString(httpResponse3.getEntity());
    println "Response Code : " + httpResponse3.getStatusLine().getStatusCode();
}