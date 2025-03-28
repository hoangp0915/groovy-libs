import groovy.json.JsonOutput

def setUserNameToEnv() {
    try {
        def userName = currentBuild.rawBuild.getCause(Cause.UserIdCause).getUserName()
        env.USER_NAME = userName
        echo "👤 User name is set to environment variables: ${userName}"
    } catch (Exception e) {
        echo "❌ Failed to set user name to environment variables. Error: ${e.getMessage()}"
    }
}

def checkoutGit(String branch) {
    echo "Checking out Git branch: ${branch}"

    def scmVars = checkout([
        $class: 'GitSCM',
        branches: [[name: branch]],
        extensions: [ lfs() ],
        userRemoteConfigs: [[url: env.GIT_URL]]
    ])

    env.GIT_COMMIT = scmVars.GIT_COMMIT
    env.GIT_BRANCH = scmVars.GIT_BRANCH
    env.GIT_URL = scmVars.GIT_URL
    env.GIT_AUTHOR_NAME = scmVars.GIT_AUTHOR_NAME
    env.GIT_AUTHOR_EMAIL = scmVars.GIT_AUTHOR_EMAIL
    env.GIT_COMMITTER_NAME = scmVars.GIT_COMMITTER_NAME
    env.GIT_COMMITTER_EMAIL = scmVars.GIT_COMMITTER_EMAIL
    env.GIT_PREVIOUS_COMMIT = scmVars.GIT_PREVIOUS_COMMIT
    env.GIT_PREVIOUS_SUCCESSFUL_COMMIT = scmVars.GIT_PREVIOUS_SUCCESSFUL_COMMIT

    echo "Checked out Git branch: ${branch}"
}

def getGitChanges() {
    def changes = ''
    if (env.GIT_PREVIOUS_SUCCESSFUL_COMMIT && env.GIT_COMMIT) {
        def command = "@git log --oneline ${env.GIT_COMMIT}...${env.GIT_PREVIOUS_SUCCESSFUL_COMMIT}"
        changes = bat(script: command, returnStdout: true).trim()
    }
    return changes
}

def buildNotificationMessage(Map params) {
    def name = params.name
    def status = params.status == 'SUCCESS' ? 'successfully' : 'failed'
    def buildUrl = env.BUILD_URL
    def revision = env.GIT_COMMIT
    def buildNumber = env.BUILD_NUMBER
    def jobName = env.JOB_NAME
    def branch = env.GIT_BRANCH

    def buildMessages = [
        "*Build aaa successfully*",
        "",
        "*Job Name*: Job Name",
        "*Build URL*: ${buildUrl}",
        "*Build number*: ${buildNumber}",
        "*Revision*: ${revision}",
        "*Branch*: ${branch}",
    ]

    if (env.USER_NAME) {
        buildMessages.add("*Started by*: ${env.USER_NAME}")
    }

    if (params.buildFileName) {
        def archived = "${env.BUILD_URL}artifact/${params.buildFileName}"
        buildMessages.add("*Archived*: ${archived}")
    }

    def changes = getGitChanges()
    if (changes) {
        buildMessages.add("*Changes:*")

        changes.split("\n").each {
            buildMessages.add("  - ${it}")
        }
    }

    return buildMessages
}

def sendTelegramNotification(Map params) {
    def chatId = params.chatId
    def text = params.text
    def messages = params.messages
    def parseMode = params.parseMode ?: "Markdown"

    if (!chatId) {
        echo "❌ Chat ID is required to send a message to Telegram."
        return
    }

    if (!text && !messages) {
        echo "❌ Text or messages are required to send a message to Telegram."
        return
    }
    echo "Sending message to Telegram..."
    def message = text ?: messages.join("\n")
    def telegramBotToken = env.TELEGRAM_BOT_TOKEN
    def url = "https://api.telegram.org/bot${telegramBotToken}/sendMessage"

    def payload = [
        chat_id: chatId,
        text: message.replaceAll("_", "\\\\_"),
        disable_notification: true,
        parse_mode: parseMode
    ]
    def jsonPayload = JsonOutput.toJson(payload)

    try {
        def response = httpRequest(
            url: url,
            httpMode: 'POST',
            requestBody: jsonPayload,
            contentType: 'APPLICATION_JSON',
            customHeaders: [[name: 'Accept', value: 'application/json']],
            validResponseCodes: '200:299',
            quiet: true
        )
        echo "✅ Sent message to ${chatId} successfully."
    } catch (Exception e) {
        echo "❌ Failed to send message to ${chatId}. Error: ${e.getMessage()}"
    }
}

return this