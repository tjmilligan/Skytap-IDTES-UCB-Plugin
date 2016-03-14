import groovyx.net.http.RESTClient
import groovyx.net.http.HttpResponseException
import groovyx.net.http.ContentType
import com.urbancode.air.AirPluginTool

def apTool = new AirPluginTool(this.args[0], this.args[1])
props = apTool.getStepProperties()
def templateID = props['templateID']
def configName = props['configName']
def username = props['username']
def password = props['password']
def proxyHost = props['proxyHost']
def proxyPort = props['proxyPort']

def unencodedAuthString = username + ":" + password
def bytes = unencodedAuthString.bytes
encodedAuthString = bytes.encodeBase64().toString()

println "Create Environment from Template Command Info:"
println "	Template ID: " + templateID
println "	Environment Name: " + configName
println "	User Name: " + username
println "	Password: " + password
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

def locked = 1

while (locked == 1) {
	try {
		locked = 0
		response = IDTESRESTClient.post(path: "configurations",
			body: ['template_id':templateID],
			requestContentType: ContentType.JSON)
	} catch (HttpResponseException ex) {
		if (ex.statusCode == 423) {
			println "Template " + templateID + " locked. Retrying..."
			locked = 1
			sleep(5000)
		} else {
			println "Unexpected Error: " + ex.statusCode
			System.exit(1)
		}
	}
}
configID = response.data.id
println "Environment ID: " + configID

if (configName) {
	loopCounter = 1
	success = 0

	while ((success == 0) && (loopCounter <=10)) {
		println "Setting Environment Name to \"" + configName + "\""
		loopCounter = loopCounter + 1
		try {
			response = IDTESRESTClient.put(path: "configurations/" + configID, query: [name:configName])
			success = 1
		} catch (HttpResponseException ex) {
			if (ex.statusCode == 400) {
				println "Message: " + ex.getMessage()
				println "Environment " + configID + " is temporarily unavailable. Retrying..."
				success = 0
				sleep(10000)
			} else if (ex.statusCode == 404) {
				println "Message: " + ex.getMessage()
				println "Environment " + configID + " is temporarily not found. Retrying..."
				success = 0
				sleep(10000)
			} else {
				println "Unexpected Error: " + ex.getMessage()
				println "Message: " + ex.statusLine
				System.exit(1)
			}
		}
	}
	if ((success == 0) && (loopCounter > 10)) {
		println "Failed to set Environment Name. Exiting."
		System.exit(1)
	}
}

apTool.setOutputProperty("buildlife/configID", configID)
apTool.setOutputProperties()
