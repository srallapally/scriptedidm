@Grapes([
        @Grab(group='org.codehaus.groovy.modules.http-builder', module='http-builder', version='0.7.1'),
        @Grab('com.github.groovy-wslite:groovy-wslite:1.1.3;transitive=false'),
        @Grab(group='org.slf4j', module='slf4j-api', version='1.6.1'),
        @Grab(group='ch.qos.logback', module='logback-classic', version='0.9.28'),
        @Grab(group='org.apache.httpcomponents', module='httpclient', version='4.5.13')
])
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClients
import org.apache.http.client.methods.HttpPatch
import org.apache.http.entity.StringEntity
import org.apache.http.util.EntityUtils
import org.apache.http.client.methods.CloseableHttpResponse

import net.sf.json.JSONNull
import net.sf.json.groovy.JsonSlurper

import org.slf4j.*
import wslite.rest.ContentType
import wslite.rest.RESTClient

import org.forgerock.openicf.connectors.groovy.OperationType
import org.forgerock.openicf.connectors.groovy.ScriptedConfiguration
import org.identityconnectors.common.logging.Log
import org.identityconnectors.framework.common.objects.Attribute
import org.identityconnectors.framework.common.objects.AttributesAccessor
import org.identityconnectors.framework.common.objects.ObjectClass
import org.identityconnectors.framework.common.objects.OperationOptions
import org.identityconnectors.framework.common.objects.Uid
import org.identityconnectors.framework.common.exceptions.ConnectorException

def operation = operation as OperationType
def configuration = configuration as ScriptedConfiguration

def objectClass = objectClass as ObjectClass
def options = options as OperationOptions
def updateAttributes = new AttributesAccessor(attributes as Set<Attribute>)
def uid = uid as Uid
def log = log as Log

String IDMURL = 'http://localhost:8080/openidm'
RESTClient client = null
client = new RESTClient(IDMURL)
client.httpClient.sslTrustAllCerts = true

println "########## Entering " + operation + " Script"
println "########## ObjectClass: " + objectClass.objectClassValue
log.ok("########## Entering " + operation + " Script")
switch (operation) {
    case OperationType.UPDATE:
        println "Entering update script for " + objectClass
        switch(objectClass){
            case ObjectClass.ACCOUNT:
                println "Updating Account for " + objectClass + ": " + uid.uidValue + " Account"
                log.ok("Updating Account for " + objectClass + ": " + uid.uidValue + " Account")
                def userName = null
                def firstName = null
                def lastName = null
                def currentRoles = []
                println "Update Payload: " + updateAttributes
            
                if (updateAttributes.hasAttribute("userName")) {
                    userName = updateAttributes.findString("userName")
                }

                if (updateAttributes.hasAttribute("sn")) {
                    firstName = updateAttributes.findString("sn")
                }

                if (updateAttributes.hasAttribute("givenName")) {
                    lastName = updateAttributes.findString("giveName")
                }
        
                if (updateAttributes.hasAttribute("roles")) {
                    println "Found roles"
                    currentRoles = updateAttributes.findList("roles")
                    println "Roles: " + currentRoles + "for " + uid.uidValue
                    def userid = getUserId(uid.uidValue)
                    def userRoles = []
                    userRoles = getUserRoles(uid.uidValue)
                    println "Roles in IDM: " + userRoles
                    // Roles to be revoked
                    if(userRoles.size() > 0){
                        userRoles.each {item ->
                            println "Checking role: " + item
                            if(!currentRoles.contains(item)){
                                println "Revoking role: " + item
                                revokeRoleFromUser(userid,item as String)
                            }
                        }
                    }
                    // Roles to be added
                    if(currentRoles.size() > 0){
                        currentRoles.each {item ->
                            println "Checking role: " + item
                            if(!userRoles.contains(item)){
                                println "Adding role: " + item
                                addRoleToUser(userid,item as String)
                            }
                        }
                    }
                }
               
               //def userid = getUserId(uid.uidValue)
               //def userRoles = []
               //userRoles = getUserRoles(uid.uidValue)
               //println "Calling Revoke Role "
               //userRoles.each {item ->
                //revokeRoleFromUser(userid,item as String)
               //}
                return userName
               break 
             case ObjectClass.GROUP:
                println "Entering update script for " + objectClass + " with attributes: " + updateAttributes + " Group"
                def groupName = null
                def groupDescription = null

                if (updateAttributes.hasAttribute("groupName")) {
                    groupName = updateAttributes.findString("groupName")
                }

                if (updateAttributes.hasAttribute("groupDescription")) {
                    groupDescription = updateAttributes.findString("groupDescription")
                }

                return groupName
            default:
                println "UpdateScript can not handle object type: " + objectClass.objectClassValue
                throw new ConnectorException("UpdateScript can not handle object type: " + objectClass.objectClassValue)
            
            
        }              
    default:
        throw new ConnectorException("UpdateScript can not handle object type: " + objectClass.objectClassValue)
}

//def getRoleDetails(String roleid){
//
//}

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

def getUserRoles(String userid){
    RESTClient client = null
    String IDMURL = 'http://localhost:8080/openidm'
    client = new RESTClient(IDMURL)
    client.httpClient.sslTrustAllCerts = true
    def path = "/managed/user?_sortKeys=userName&_fields=*" + "&_queryFilter=userName+eq+%22" + userid + "%22"
    println "getUserRoles:" + path
    def response = client.get(path: path,
                  headers: ['X-OpenIDM-Username': 'openidm-admin',
                          "X-OpenIDM-Password": 'openidm-admin',
                          "Accept-API-Version": "resource=1.0"])
    def roleList = []                      
    response.json.result.each { item ->
            println "Effective Roles: " + item.effectiveRoles
            item.effectiveRoles.each { role ->
                roleList.add(role._refResourceId)
            }
    }        
    if(roleList.size() == 0){
        println "No roles found for user: " + userid
    }

    return roleList

}
def addRoleToUser(String userId,String roleId){
    def grantString = "[{\"operation\": \"add\",\"field\": \"/roles/-\",\"value\": {\"_ref\" : \"managed/role/"+roleId+"\"}}]"
    log.ok("This is my grant string: " + grantString)
    apacheHttpRoleGrant(userId, grantString)
}
def revokeRoleFromUser(String userId, String roleId){
    RESTClient client = null
    String IDMURL = 'http://localhost:8080/openidm'
    client = new RESTClient(IDMURL)
    client.httpClient.sslTrustAllCerts = true
    //def userId = getUserId(userName)
    def path = "/managed/user/"+userId+"/roles?_queryFilter=_refResourceId%20eq%20%22"+roleId+"%22&_fields=_ref/*,name"
    def response = client.get(path: path,
            headers: ['X-OpenIDM-Username': 'openidm-admin',
                      "X-OpenIDM-Password": 'openidm-admin',
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
    def roleGrantStr = "http://localhost:8080/openidm/managed/user/"+userId
    println "This is my role grant string: " + grantString
    CloseableHttpClient httpClient2 =  HttpClients.createDefault();
    HttpPatch httpPatch2 = new HttpPatch(roleGrantStr);
    httpPatch2.setHeader("X-OpenIDM-Username",'openidm-admin');
    httpPatch2.setHeader("X-OpenIDM-Password", 'openidm-admin');
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
    def roleDeleteStr = "http://localhost:8080/openidm/managed/user/"+userId
    println "This is my role delete string: " + roleDeleteStr
    CloseableHttpClient httpClient3 =  HttpClients.createDefault();
    HttpPatch httpPatch3 = new HttpPatch(roleDeleteStr);
    httpPatch3.setHeader("X-OpenIDM-Username",'openidm-admin');
    httpPatch3.setHeader("X-OpenIDM-Password", 'openidm-admin');
    httpPatch3.setHeader("Accept-API-Version", "resource=1.0");
    httpPatch3.setHeader("Content-Type", "application/json");
    StringEntity entity3 = new StringEntity(revokeString);
    //entity.setContentType();
    httpPatch3.setEntity(entity3);
    CloseableHttpResponse httpResponse3 = httpClient3.execute(httpPatch3);
    String msg = EntityUtils.toString(httpResponse3.getEntity());
    println "Response Code : " + httpResponse3.getStatusLine().getStatusCode();
}