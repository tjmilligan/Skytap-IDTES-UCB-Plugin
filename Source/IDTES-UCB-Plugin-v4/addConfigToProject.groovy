import groovyx.net.http.RESTClient
import groovyx.net.http.HttpResponseException
import groovyx.net.http.ContentType
import com.urbancode.air.AirPluginTool

def apTool = new AirPluginTool(this.args[0], this.args[1])
props = apTool.getStepProperties()
def configID = props['configID']
def projectName = props['projectName']
def username = props['username']
def password = props['password']
def proxyHost = props['proxyHost']
def proxyPort = props['proxyPort']
def verboseLogging = props['verboseLogging']

def unencodedAuthString = username + ":" + password
def bytes = unencodedAuthString.bytes
def encodedAuthString = bytes.encodeBase64().toString()

println "Add Environment to Project Info:"
println "	Environment ID: " + configID
println "	Project Name: " + projectName
println "	Proxy Host: " + proxyHost
println "	Proxy Port: " + proxyPort
println "Done"

def IDTESRESTClient = new RESTClient('https://cloud.skytap.com/')
IDTESRESTClient.defaultRequestHeaders.'Authorization: Basic' = encodedAuthString
IDTESRESTClient.defaultRequestHeaders.'Accept' = "application/json"
IDTESRESTClient.defaultRequestHeaders.'Content-Type' = "application/json"

if (proxyHost) {
	if (proxyPort) {
		IDTESRESTClient.setProxy(proxyHost, proxyPort.toInteger(), "http")
	} else {
		println "Error: Proxy Host was specified but no Proxy Port was specified"
		System.exit(1)
	}
}
//
//  Get the project ID of the specified Project Name
//

if (projectName) {
	projectID = 0
	response = IDTESRESTClient.get(path: "projects")
	projectList = response.data

	projectList.each {
        	if (it.name == projectName) {
                	println "Found Project Name: " + it.name
                	println "Project ID: " + it.id
                	projectID = it.id
        	}
	}
	if (projectID == 0) {
		System.err.println "Error: Project \"" + projectName + "\" not found."
		System.exit(1)
	}
}

try {
projadd_path = "projects/" + projectID + "/configurations/" + configID
response = IDTESRESTClient.post(path: projadd_path,
	requestContentType: ContentType.JSON)
} catch (HttpResponseException ex) {
	if (ex.statusCode == 423) {
		println "Enviornment " + configID + " locked."
		System.exit(1)
	} else {
		println "Unexpected Error: " + ex.statusCode
		System.exit(1)
	}
}

println "Added Environment ID \"" + configID + "\" to Project ID \"" + projectID + "\"" 
