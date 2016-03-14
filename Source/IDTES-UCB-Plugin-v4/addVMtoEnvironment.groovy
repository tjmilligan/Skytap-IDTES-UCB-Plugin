import groovyx.net.http.RESTClient
import groovyx.net.http.HttpResponseException
import groovyx.net.http.ContentType
import com.urbancode.air.AirPluginTool

def apTool = new AirPluginTool(this.args[0], this.args[1])
props = apTool.getStepProperties()

def configID = props['configID']
def templateID = props['templateID']
def vmName = props['vmName']
def newVMName = props['newVMName']
def username = props['username']
def password = props['password']
def proxyHost = props['proxyHost']
def proxyPort = props['proxyPort']

def unencodedAuthString = username + ":" + password
def bytes = unencodedAuthString.bytes
encodedAuthString = bytes.encodeBase64().toString()

println "Add VM to Environment Info:"
println "	Environment ID: " + configID
println "	Template ID: " + templateID
println "	VM Name or ID: " + vmName
println "	New VM Name: " + newVMName
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
// Check if a VM ID was specified or if it was a name
//

if (vmName.isInteger()) {
	println "VM ID " + vmName + " was specified"
	vmID = vmName.toInteger()
	//
	// Get the name of the specified VM
	//
	loopCounter = 1
	locked = 1
	while ((loopCounter <= 12) && (locked == 1)) {
		try {
			loopCounter = loopCounter + 1
			locked = 0
			response = IDTESRESTClient.get(path: "templates/" + templateID + "/vms/" + vmID)
		} catch(HttpResponseException ex) {
			if ((ex.statusCode == 423) || (ex.statusCode == 500)) {
				println "Looking for specified VM but Environment is locked or busy. Retrying..."
				locked = 1
				sleep(10000)
			} else {
				System.err.println "Unexpected Error: " + ex.statusCode + " - " + ex.getMessage()
				System.err.println "URL: https://cloud.skytap.com/templates/" + templateID + "/vms/" + vmID
				System.exit(1)
			}
		}
	}
	if (loopCounter > 13) {
		System.err.println "VM get info operation timed out. Aborting process."
		System.exit(1)
	}
	vmName = response.data.name
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
		response = IDTESRESTClient.get(path: "templates/" + templateID)
	} catch(HttpResponseException ex) {
		if ((ex.statusCode == 423) || (ex.statusCode == 500)) {
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
		if (it.name == vmName.toString()) {
			println "Found VM in Template with Name: " + it.name
			vmID = it.id
		}
	}

	if (vmID == 0) {
		System.err.println "Error: VM Name \"" + vmName + "\" not found"
		System.exit (1)
	}
	println "Found VM in Template with ID: " + vmID
}
loopCounter = 1
locked = 1
while ((loopCounter <= 12) && (locked == 1)) {
	try {
		loopCounter = loopCounter + 1
		locked = 0
		response = IDTESRESTClient.put(path: "configurations/" + configID,
			body: [
				'template_id':templateID,
				'vm_ids[]':vmID
			],
			requestContentType: ContentType.JSON)
	} catch(HttpResponseException ex) {
		if ((ex.statusCode == 423) || (ex.statusCode == 500) || (ex.statusCode == 422)) {
			println "Environment is locked or busy. Retrying..."
			locked = 1
			sleep(10000)
		} else {
			System.err.println "Unexpected Error: " + ex.statusCode + " - " + ex.getMessage()
			System.err.println "URL: https://cloud.skytap.com/configurations/" + configID
			System.exit(1)
		}
	}
}

if (loopCounter > 13) {
	System.err.println "VM add operation timed out. Aborting process."
	System.exit(1)
}


//
// Find the ID of the newly added VM in the environment
//

response = IDTESRESTClient.get(path: "configurations/" + configID)

newVMID = 0
vmList = response.data.vms

vmList.each {
	if (it.name == vmName.toString()) {
		println "Found VM in Configuration with Name: " + it.name
		newVMID = it.id
	}
}

if (newVMID == 0) {
	System.err.println "Error: VM Name \"" + vmName + "\" not found"
	System.exit (1)
}
println "Found VM in Configuration with ID: " + newVMID

//
// If a new name was specified, change the name of the newly added VM in the environment
//
if (newVMName) {

	loopCounter = 1
	success = 0

	while ((success == 0) && (loopCounter <=10)) {
		println "Setting VM Name to \"" + newVMName + "\""
		loopCounter = loopCounter + 1
		try {
			response = IDTESRESTClient.put(path: "configurations/" + configID + "/vms/" + newVMID, query: [name:newVMName])
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
println "Added VM " + vmName + " from Template " + templateID + " to Environment " + configID
if (newVMName) {
	println "Changed name of VM " + vmName + " to " + newVMName
}

apTool.setOutputProperty("buildlife/vmID", newVMID)
apTool.setOutputProperties()
