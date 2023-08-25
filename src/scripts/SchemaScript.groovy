import org.forgerock.openicf.connectors.groovy.OperationType
import org.forgerock.openicf.connectors.groovy.ScriptedConfiguration
import org.identityconnectors.common.logging.Log
import org.identityconnectors.common.security.GuardedByteArray
import org.identityconnectors.common.security.GuardedString
import org.identityconnectors.framework.common.objects.ObjectClass
import org.identityconnectors.framework.common.objects.OperationOptionInfoBuilder
import org.identityconnectors.framework.common.objects.OperationalAttributeInfos
import org.identityconnectors.framework.common.objects.PredefinedAttributeInfos
import org.identityconnectors.framework.spi.operations.AuthenticateOp
import org.identityconnectors.framework.spi.operations.ResolveUsernameOp
import org.identityconnectors.framework.spi.operations.SchemaOp
import org.identityconnectors.framework.spi.operations.ScriptOnConnectorOp
import org.identityconnectors.framework.spi.operations.ScriptOnResourceOp
import org.identityconnectors.framework.spi.operations.SearchOp
import org.identityconnectors.framework.spi.operations.SyncOp
import org.identityconnectors.framework.spi.operations.TestOp

import static org.identityconnectors.framework.common.objects.AttributeInfo.Flags.MULTIVALUED
import static org.identityconnectors.framework.common.objects.AttributeInfo.Flags.NOT_CREATABLE
import static org.identityconnectors.framework.common.objects.AttributeInfo.Flags.NOT_READABLE
import static org.identityconnectors.framework.common.objects.AttributeInfo.Flags.NOT_RETURNED_BY_DEFAULT
import static org.identityconnectors.framework.common.objects.AttributeInfo.Flags.NOT_UPDATEABLE
import static org.identityconnectors.framework.common.objects.AttributeInfo.Flags.REQUIRED

def operation = operation as OperationType
def configuration = configuration as ScriptedConfiguration
def log = log as Log

return builder.schema({
    objectClass {
        type ObjectClass.ACCOUNT_NAME
        attributes {
            userName String.class, REQUIRED
            employeeId String.class, REQUIRED
            displayName String.class, REQUIRED
            sn String.class, REQUIRED
            mail String.class, REQUIRED
            accountStatus String.class, REQUIRED
            givenName String.class, REQUIRED
            preferredFirstName String.class, REQUIRED
            middleName String.class
            description String.class
            telephoneNumber String.class
            fax String.class
            workPhone String.class
            postalAddress String.class
            city String.class
            postalCode String.class
            country String.class
            stateProvince String.class
            location String.class
            officeAddress String.class
            officeZipCode String.class
            street String.class
            region String.class
            activeDate String.class
            inactiveDate String.class
            terminationDate String.class
            employeeType String.class
            actionCode String.class
            authoritativeSource String.class
            createContractorEmail String.class
            createContractorADAccount String.class
            contractorEmail String.class
            companyDescription String.class
            companyName String.class
            costCenter String.class
            cubeBuilding String.class
            cubeNumber String.class
            departmentName String.class
            departmentNumber String.class
            jobCode String.class
            jobCodeDescription String.class
            jobFamily String.class
            jobFunction String.class
            adDomain String.class
            colid String.class
            empCode String.class
            userNameGenerated String.class
            employeeEffectiveDate String.class
            eudAccounts String.class
            mobilePhoneMFA String.class
            userPrincipalName String.class
            azureAccountStatus String.class
            roles String.class, MULTIVALUED
        }

    }
    objectClass {
        type ObjectClass.GROUP_NAME
        attributes {
            roleName String.class, REQUIRED
            roleDescription String.class
            appName String.class
            roleType String.class
            roleSubType String.class
            attribute String.class
            objectName String.class
        }
    }

    defineOperationOption OperationOptionInfoBuilder.buildPagedResultsCookie(), SearchOp
    defineOperationOption OperationOptionInfoBuilder.buildPagedResultsOffset(), SearchOp
    defineOperationOption OperationOptionInfoBuilder.buildPageSize(), SearchOp
    defineOperationOption OperationOptionInfoBuilder.buildSortKeys(), SearchOp
    defineOperationOption OperationOptionInfoBuilder.buildRunWithUser()
    defineOperationOption OperationOptionInfoBuilder.buildRunWithPassword()
    }
)

