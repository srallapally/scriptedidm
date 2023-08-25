@Grab('com.xlson.groovycsv:groovycsv:1.3')
import org.forgerock.openicf.connectors.groovy.OperationType
import org.forgerock.openicf.connectors.groovy.ScriptedConfiguration
import org.identityconnectors.common.logging.Log
import org.identityconnectors.framework.common.exceptions.ConnectorException
import org.identityconnectors.framework.common.objects.*

def operation = operation as OperationType
def configuration = configuration as ScriptedConfiguration

def objectClass = objectClass as ObjectClass
def options = options as OperationOptions
def updateAttributes = new AttributesAccessor(attributes as Set<Attribute>)
def uid = uid as Uid
def log = log as Log


switch (operation) {
    case OperationType.UPDATE:
        println "Entering update script for " + objectClass
        switch(objectClass){
            case ObjectClass.ACCOUNT:
                def fileLocation = configuration.propertyBag.__ACCOUNT__.fileloc

                def userName = null
                def firstName = null
                def lastName = null
                def groups = []

                if (updateAttributes.hasAttribute("userName")) {
                    userName = updateAttributes.findString("userName")
                }

                if (updateAttributes.hasAttribute("firstName")) {
                    firstName = updateAttributes.findString("firstName")
                }

                if (updateAttributes.hasAttribute("lastName")) {
                    lastName = updateAttributes.findString("lastName")
                }
        
                if (updateAttributes.hasAttribute("groups")) {
                    groups = updateAttributes.findStringList("groups")
                }
                def result = writeAccountFileWithLock(fileLocation, header, userName, firstName, lastName, groups,log)
                if(result == true){
                    println "Updated file"
                    log.info("Updated file")
                } else {
                    throw new ConnectorException("Failed to update file")
                }
                return userName
             case ObjectClass.GROUP:
                def fileLocation = configuration.propertyBag.__GROUP__.fileloc
                def header = ["GROUP_NAME","GROUP_DESC"]

                def groupName = null
                def groupDescription = null

                if (updateAttributes.hasAttribute("groupName")) {
                    groupName = updateAttributes.findString("groupName")
                }

                if (updateAttributes.hasAttribute("groupDescription")) {
                    groupDescription = updateAttributes.findString("groupDescription")
                }

                def result = writeGroupFileWithLock(fileLocation, header, groupName, groupDescription,log)
                if(result == true){
                    println "Updated file"
                    log.info("Updated file")
                } else {
                    throw new ConnectorException("Failed to update file")
                } 

                return groupName
            default:
                throw new ConnectorException("UpdateScript can not handle object type: " + objectClass.objectClassValue)
            
            
        }              
    default:
        throw new ConnectorException("UpdateScript can not handle object type: " + objectClass.objectClassValue)
}

def writeGroupFileWithLock(String filePath, ArrayList header, String groupName, String groupDescription, Log logger){
    return true
}

def writeAccountFileWithLock(String filePath, ArrayList header, String userName, String firstName, String lastName, ArrayList groups, Log logger){
    return true
}