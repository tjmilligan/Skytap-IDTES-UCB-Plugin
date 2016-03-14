import com.urbancode.air.AirPluginTool

def apTool = new AirPluginTool(this.args[0], this.args[1])
props = apTool.getStepProperties()

def username = props['username']
def password = props['password']

println "IDTES Authentication Parameters Created"

apTool.setOutputProperty("buildlife/username", username)
apTool.setOutputProperty("buildlife/password", password)
apTool.setOutputProperties()
