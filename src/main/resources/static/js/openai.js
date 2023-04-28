let keepText = "";
const apiUrl = "/api/openai";
const uuid = getUuid();
let number = new Date().getSeconds();
const heartbeatMsg = "this is the heartbeat message"

// WS连接地址
const wsServer= (window.location.protocol+'//' + window.location.host + "/api/ws/" + uuid)
    .replace("http", "ws")
    .replace("https", "wss");

//socket初始化
let socket;
// 重连次数
let reconnectCount;
webSocketInit();
function webSocketInit() {

    // 实例化WebSocket
    socket = new WebSocket(wsServer);

    //打开事件
    socket.onopen = function () {
        reconnectCount = 1;
        console.log("WebSockets连接已建立，正在等待数据...");
    };

    //关闭事件
    socket.onclose = function () {
        if (reconnectCount <= 15) {
            console.log("WebSockets连接关闭了，正在尝试第"+ reconnectCount++ +"次重新连接...");
            reconnect();
        } else {
            console.log("WebSockets连接关闭了，重连次数超过15次，停止重连，如需继续使用，请刷新页面");
            toast({ time: 6000, msg: "WebSockets连接关闭了，重连次数超过15次，停止重连，如需继续使用，请刷新页面" });
        }
    };

    //获得消息事件
    socket.onmessage = function (msg) {
        // 过滤心跳信息
        if (msg.data !== heartbeatMsg) {
            const id = idVal.val()
            const contentHtml = $("#content" + number)
            const articleWrapper = $("#article-wrapper");
            if(id === "1"){
                keepText += msg.data;
                contentHtml.removeClass("hide-class");
                contentHtml.find("pre").html(contentHtml.find("pre").html() + msg.data);
            } else {
                articleWrapper.append(
                    '<li class="article-content" id=content' + number + '><img src="' + msg.data + '" alt=""></li>'
                );
            }
            $(".creating-loading").removeClass("isLoading");
        }
    };
}

// 重连
function reconnect() {
    setTimeout(function () {
        webSocketInit();
    }, 2000);
}

const apikeyInput = $("#apikey");
const kwTarget = $("#kw-target");
const keepVal = $("#keep");
const idVal = $("#id");

// 读取localStorage内的数据
let apikey = localStorage.getItem("apikey");
if (apikey != null) {
    apikeyInput.val(apikey);
}

// 监听输入问题回车事件
kwTarget.keydown(function (e) {
    // 当 keyCode 是13时,是回车操作
    if (e.keyCode === 13) {
        aiClick();
    }
});

// 监听Key回车事件
apikeyInput.keydown(function (e) {
    // 当 keyCode 是13时,是回车操作
    if (e.keyCode === 13) {
        // 查询余额
        keyclick();
    }
});

// 查询余额方法
function keyclick() {
    apikey = apikeyInput.val();
    const keyVal = localStorage.getItem("apikey");
    // 将输入的apikey存入localStorage
    localStorage.setItem("apikey", apikey);
    if (!apikey) {
        if(keyVal){
            toast({ time: 1000, msg: "APIKEY清除成功！" });
        }
        return;
    }
    toast({ time: 1000, msg: "APIKEY设置成功！" });
}

// 点击事件
function aiClick() {
    const title = kwTarget.val()
    if (!title) {
        return toast({ time: 2000, msg: "来问点什么吧" });
    }
    createArticle(title);
}

// 请求AI回复方法
function createArticle(title) {
    apikey = apikeyInput.val();

    $("#article").removeClass("created");

    const id = idVal.val()

    if(keepVal.val() === "1"){
        keepText += (keepText === "" ? "" : "\n") + "user:" + title + "・・assistant:"
    }

    const data = JSON.stringify({
        text: title, id: id, apikey: apikey, keep: keepVal.val(), keepText: keepText,
    })

    const articleWrapper = $("#article-wrapper");

    articleWrapper.append(
        '<li class="article-title">Me: ' + title + "<li>"
    );

    $(".creating-loading").addClass("isLoading");

    number = new Date().getSeconds();

    kwTarget.val("")

    if(idVal.val() === "1"){
        articleWrapper.append(
            '<li class="article-content hide-class" id=content' +
            number +
            "><pre></pre></li>"
        );
    }

    socket.send(data)
}

// 连续对话开关
function keepChange() {
    if (keepVal.val() === "1") {
        toast({
            time: 4000,
            msg: "连续对话已打开，请求受Token的长度影响，建议使用自己的APIKey",
        });
    } else {
        toast({ time: 2000, msg: "连续对话已关闭" });
    }
}

// 清空聊天记录
function clearReply() {
    keepText = "";
    $("#article-wrapper").html("");
    return toast({ time: 2000, msg: "聊天记录已清空！" });
}

function getUuid() {
    return 'xxxxxxxxxxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function (c) {
        var r = (Math.random() * 16) | 0,
            v = c === 'x' ? r : (r & 0x3) | 0x8;
        return v.toString(16);
    });
}