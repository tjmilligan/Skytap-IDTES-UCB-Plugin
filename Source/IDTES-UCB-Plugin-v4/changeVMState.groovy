import com.urbancode.air.AirPluginTool
import groovyx.net.http.HttpResponseException
import groovyx.net.http.RESTClient
import groovyx.net.http.ContentType

def apTool = new AirPluginTool(this.args[0], this.args[1])
props = apTool.getStepProperties()
// def configID = apTool.getStepProperties()['configID']
def configID = props['configID']
def vmName = props['vmName']
def newState = props['newState']
def username = props['username']
def password = props['password']
def proxyHost = props['proxyHost']
def proxyPort = props['proxyPort']

def unencodedAuthString = username + ":" + password
def bytes = unencodedAuthString.bytes
encodedAuthString = bytes.encodeBase64().toString()

println "Change Environment State Command Info:"
println "	Environment ID: " + configID
println "	VM Name or ID: " + vmName
println "	New State: " + newState
println "	User Name: " + username
println "	Password: " + password
println "	Proxy Host: " + proxyHost
println "	Proxy Port: " + proxyPort
println "Done"

def IDTESRESTClient = new RESTClient('https://cloud.skytap.com/')
IDTESRESTClient.defaultRequestHeaders.'Authorization' = 'Basic ' + encodedAuthString
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
// Check if a VM ID was specified or if it was a name
//

if (vmName.isNumber()) {
	println "VM ID " + vmName + " was specified"
	urlPath = "configurations/" + configID + "/vms/" + vmName
	println "DEBUG 1 - urlPath: " + urlPath
} else { 
	//
	// If the ID wasn't specified, it must be a name, so we need to find it
	//

	loopCounter = 1
	locked = 1
	while ((loopCounter <= 12) && (locked == 1)) {
		try {
			loopCounter = loopCounter + 1
			locked = 0
			response = IDTESRESTClient.get(path: "configurations/" + configID)
		} catch(HttpResponseException ex) {
			if ((ex.statusCode == 423) || (ex.statusCode == 500) || (ex.statusCode == 422)) {
				println "Looking for specified VM but Environment is locked or busy. Retrying..."
				locked = 1
				sleep(10000)
			} else {
				System.err.println "Unexpected Error: " + ex.statusCode + " - " + ex.getMessage()
				System.err.println "URL: https://cloud.skytap.com/templates/" + templateID
				System.exit(1)
			}
		}
	}
	if (loopCounter > 13) {
		System.err.println "VM get info operation timed out. Aborting process."
		System.exit(1)
	}

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
		System.exit(1)
	}
	println "Found VM ID: " + vmID
	urlPath = "configurations/" + configID + "/vms/" + vmID
	println "DEBUG 2 - urlPath: " + urlPath
}
loopCounter = 1
locked = 1
while ((loopCounter <= 30) && (locked == 1)) {
	loopCounter = loopCounter + 1
	locked = 0
	try {
		response = IDTESRESTClient.get(path: urlPath)
	} catch(HttpResponseException ex) {
		if ((ex.statusCode == 423) || (ex.statusCode == 500) || (ex.statusCode == 422)) {
			println "Environment is locked or busy. Retrying..."
			locked = 1
			sleep(10000)
		} else {
			System.err.println "Unexpected Error: " + ex.statusCode + " - " + ex.getMessage()
			System.exit(1)
		}
	}
}
if (loopCounter > 31) {
	System.err.println "VM add operation timed out. Aborting process."
	System.exit(1)
}
println "Initial Run State is \"" + response.data.runstate + "\""

def loopCounter = 1
while ((response.data.runstate == "busy") && (loopCounter <= 12)) {
	println "Environment is busy, waiting for it to be ready..."
	sleep(10000)
	response = IDTESRESTClient.get(path: urlPath)
	loopCounter = loopCounter + 1
}

if (response.data.runstate != "busy") {

loopCounter = 1
locked = 1
while ((loopCounter <= 30) && (locked == 1)) {
	loopCounter = loopCounter + 1
	locked = 0
	try {
		response = IDTESRESTClient.put(path: urlPath, query:[runstate:newState] )
	} catch(HttpResponseException ex) {
		if ((ex.statusCode == 423) || (ex.statusCode == 500)) {
			println "Environment is locked or busy. Retrying..."
			locked = 1
			sleep(10000)
		} else {
			System.err.println "Unexpected Error: " + ex.statusCode + " - " + ex.getMessage()
			System.exit(1)
		}
	}
}
if (loopCounter > 31) {
	System.err.println "VM add operation timed out. Aborting process."
	System.exit(1)
}

	def innerLoopCounter = 1
	while ((response.data.runstate != newState) && (innerLoopCounter <= 30)) {
		println "Waiting for environment " + configID + " to transition to \"" + newState + "\" state"
		sleep(10000)
		println "Checking on environment state"
		response = IDTESRESTClient.get(path: urlPath)
		println "Run State is \"" + response.data.runstate + "\""
		if ((response.data.runstate != "busy") && (response.data.runstate != newState)) {
			response = IDTESRESTClient.put(path: urlPath, query:[runstate:newState])
		}
		innerLoopCounter = innerLoopCounter + 1
	}
	if (response.data.runstate != newState) {
		System.err.println "Error: Environment " + configID + " never reached the " + newState + " state"
		System.exit(1)
	}
} else {
	System.err.println "Error: Environment " + configID + " never left the \"busy\" state"
	System.exit(1)
}
