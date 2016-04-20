import groovyx.net.http.RESTClient
import groovyx.net.http.HttpResponseException
import groovyx.net.http.ContentType
import com.urbancode.air.AirPluginTool

def apTool = new AirPluginTool(this.args[0], this.args[1])
props = apTool.getStepProperties()

def configID = props['configID']
def netName = props['netName']
def vpnID = props['vpnID']
def username = props['username']
def password = props['password']
def proxyHost = props['proxyHost']
def proxyPort = props['proxyPort']

def unencodedAuthString = username + ":" + password
def bytes = unencodedAuthString.bytes
encodedAuthString = bytes.encodeBase64().toString()

println "Connect Environment to VPN Info:"
println "	Environment ID: " + configID
println "	Network Name: " + netName
println "	VPN ID: " + vpnID
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
// Get Network ID
//
try {
	response = IDTESRESTClient.get(path: "configurations/" + configID)
} catch(HttpResponseException ex) {
	println "Message: " + ex.getMessage()
	println "Code: " + ex.statusCode
}

networkList = response.data.networks
networkFound = 0
networkList.each {
	if (it.name == netName) {
		println "Found Network: \"" + it.name + "\" ID: " + it.id
		netID = it.id
		networkFound = 1
	}
}
if (networkFound == 0) {
	System.err.println "Error: Network " + netName + " not found"
	System.exit(1)
}



//
// Check to see if the environment is already connected to the VPN
//
vpnConnected = 0
vpnAttached = 1
try {
	response = IDTESRESTClient.get(path: "configurations/" + configID + "/networks/" + netID + "/vpns/" + vpnID)
} catch(HttpResponseException ex) {
	if (ex.response.data.keySet().contains("error")) {
		if (ex.response.data.error.contains("Configuration not attached to VPN")) {
			vpnAttached = 0
		} else {
			System.err.println "Unexpected Error: " + ex.statusCode + " - " + ex.getMessage()
			System.exit(1)
		}
	}
}

if (response.data.keySet().contains("connected")) {
	if (response.data.connected) {
		vpnConnected = 1
	}
}

if (vpnAttached == 1) {
	println "VPN is Attached"
} else {
	println "VPN is not attached"
	println "Attaching VPN to Environment..."
	
	loopCounter = 1
	locked = 1
	while ((loopCounter <= 12) && (locked == 1)) {
		try {
			loopCounter = loopCounter + 1
			locked = 0
			response = IDTESRESTClient.post(path: "configurations/" + configID + "/networks/" + netID + "/vpns",
				body: ['vpn_id':vpnID],
				requestContentType: ContentType.JSON)
		} catch(HttpResponseException ex) {
			if ((ex.statusCode == 423) || (ex.statusCode == 500)) {
				println "VPN is locked or busy. Retrying..."
				locked = 1
				sleep(10000)
			} else {
				System.err.println "Unexpected Error: " + ex.statusCode + " - " + ex.getMessage()
				System.exit(1)
			}
		}
	}
}

try {
	response = IDTESRESTClient.get(path: "configurations/" + configID + "/networks/" + netID + "/vpns/" + vpnID)
} catch(HttpResponseException ex) {
	if (ex.response.data.keySet().contains("error")) {
		if (ex.response.data.error.contains("Configuration not attached to VPN")) {
			System.err.println "Failed to attach to VPN. Exiting."
			System.exit(1)
		} else {
			System.err.println "Unexpected Error: " + ex.statusCode + " - " + ex.getMessage()
			System.exit(1)
		}
	}
}

if (vpnConnected == 1) {
	println "VPN is connected"
} else {
	println "Connecting VPN"
	loopCounter = 1
	locked = 1
	while ((loopCounter <= 12) && (locked == 1)) {
		try {
			loopCounter = loopCounter + 1
			locked = 0
			response = IDTESRESTClient.put(path: "configurations/" + configID + "/networks/" + netID + "/vpns/" + vpnID,
				body: ['connected':"true"],
				requestContentType: ContentType.JSON)
		} catch(HttpResponseException ex) {
			if ((ex.statusCode == 423) || (ex.statusCode == 500)) {
				println "VPN is locked or busy. Retrying..."
				locked = 1
				sleep(10000)
			} else {
				System.err.println "Unexpected Error: " + ex.statusCode + " - " + ex.getMessage()
				System.exit(1)
			}
		}
	}
}

try {
	response = IDTESRESTClient.get(path: "configurations/" + configID + "/networks/" + netID + "/vpns/" + vpnID)
} catch(HttpResponseException ex) {
	if (ex.response.data.keySet().contains("error")) {
		if (ex.response.data.error.contains("Configuration not attached to VPN")) {
			System.err.println "Error: The Environment became unexpectedly detatched from the VPN"
			System.exit(1)
		} else {
			System.err.println "Unexpected Error: " + ex.statusCode + " - " + ex.getMessage()
			System.exit(1)
		}
	}
}

if (response.data.keySet().contains("connected")) {
	if (! response.data.connected) {
		System.err.println "Error: The VPN failed to connect. Exiting."
		System.exit(1)
	}
}

println "VPN " + vpnID + " is attached and connected to Environment " + configID


