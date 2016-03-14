import groovyx.net.http.RESTClient
import groovyx.net.http.HttpResponseException
import groovyx.net.http.ContentType
import com.urbancode.air.AirPluginTool

def apTool = new AirPluginTool(this.args[0], this.args[1])
props = apTool.getStepProperties()
def configID = props['configID']
def vmName = props['vmName']
def username = props['username']
def password = props['password']
def proxyHost = props['proxyHost']
def proxyPort = props['proxyPort']

def unencodedAuthString = username + ":" + password
def bytes = unencodedAuthString.bytes
encodedAuthString = bytes.encodeBase64().toString()

println "Delete Environment Command Info:"
println "	Environment ID: " + configID
println "	Proxy Host: " + proxyHost
println "	Proxy Port: " + proxyPort
println "Done"

def IDTESRESTClient = new RESTClient('https://cloud.skytap.com/')
IDTESRESTClient.defaultRequestHeaders.'Authorization: Basic' = encodedAuthString
// IDTESRESTClient.defaultRequestHeaders.'Accept' = "application/json"
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
// Check if a VM ID was specified or if it was a name
//

if (vmName.isNumber()) {
	println "VM ID " + vmName + " was specified"
	urlPath = "configurations/" + configID + "/vms/" + vmName
} else { 
	//
	// If the ID wasn't specified, it must be a name, so we need to find it
	//

	response = IDTESRESTClient.get(path: "configurations/" + configID)

	vmID = 0
	vmList = response.data.vms

	vmList.each {
		if (it.name == vmName) {
			println "Found VM Name: " + it.name
			vmID = it.id
		}
	}

	if (vmID == 0) {
		System.err.println "Error: VM Name \"" + vmName + "\" not found"
		exit (1)
	}
	println "Found VM ID: " + vmID
	urlPath = "configurations/" + configID + "/vms/" + vmID
}
def locked = 1
def loopCounter = 1

while ((loopCounter <= 30) && (locked == 1)) {
	loopCounter = loopCounter + 1
	try {
		locked = 0
		response = IDTESRESTClient.delete(path: urlPath,
			requestContentType: ContentType.JSON)
	} catch (HttpResponseException ex) {
		if ((ex.statusCode == 423) || (ex.statusCode == 500)) {
			println "VM " + vmName + " or Environment " + configID + " locked or busy. Retrying..."
			locked = 1
			sleep(10000)
		} else if (ex.statusCode == 404) {
			System.err.println ex.statusCode + " - Not Found: " + "https://cloud.skytap.com/" + urlPath
			System.exit(1)
		} else {
			System.err.println "Unexpected Error: " + ex.statusCode + " - " + ex.getMessage()
			System.exit(1)
		}
	}
}

println "VM " + vmName + " deleted from environment " + configID + " deleted"
