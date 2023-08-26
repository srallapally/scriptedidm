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
import org.identityconnectors.framework.common.objects.Attribute
import org.identityconnectors.framework.common.objects.AttributesAccessor
import org.identityconnectors.framework.common.objects.ObjectClass
import org.identityconnectors.framework.common.objects.OperationOptions
import org.identityconnectors.framework.common.exceptions.ConnectorException

def operation = operation as OperationType
def configuration = configuration as ScriptedConfiguration

def objectClass = objectClass as ObjectClass
def options = options as OperationOptions
def log = log as Log

def openidm_user = null
def openidm_password = null
String IDMURL = 'http://localhost:8080/openidm'
RESTClient client = null
client = new RESTClient(IDMURL)
client.httpClient.sslTrustAllCerts = true

println "########## Entering " + operation + " Script"

switch(objectClass){
    case ObjectClass.ACCOUNT:
        println "Deleting Account for " + objectClass + ": " + uid.uidValue + " Account"
        def id = null
        id = getUserId(uid.uidValue,client)
        if(id) {
            def path = "/managed/user/" + id
            def response = client.delete(path: path,
                  headers: ['X-OpenIDM-Username': 'openidm-admin',
                          "X-OpenIDM-Password": 'openidm-admin',
                          "Accept-API-Version": "resource=1.0"])
            return true
        } else {
            println "User not found "+uid.uidValue
            return false
        }    
    case ObjectClass.GROUP:
        throw new ConnectorException("Deleting object of type: " + objectClass.objectClassValue + " is not supported")
            
}

def getUserId(String userName, RESTClient client){
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
    println "User ID: " + userid + " for user " + userName
    return userid
}

            