-- post.lua

local body   = '{}'

local hdrs = {
  ["Host"]            = "localhost:8080",
  ["Accept"]          = "*/*",
  ["Accept-Encoding"] = "gzip, deflate, br",
  ["Connection"]      = "keep-alive",
  ["User-Agent"]      = "PostmanRuntime-ApipostRuntime/1.1.0",
}

-- 文件路径
local stats_file = "/tmp/wrk_table_stats_" .. os.time() .. ".log"

-- 初始化
function init(args)
    wrk.method  = "POST"
    wrk.headers = hdrs
    wrk.body    = body 
end

-- 请求
function request()
    return wrk.format()
end

-- 响应 - 记录状态码和响应体
function response(status, headers, body)
    local code = tostring(status or "unknown")
    local body_content = "empty"
    
    if body and #body > 0 then
        -- 清理响应内容：移除首尾空格和换行
        body_content = body:gsub("^%s+", ""):gsub("%s+$", ""):gsub("%s+", " ")
    end
    
    -- 创建记录：状态码|响应内容
    local record = code .. "|" .. body_content
    
    -- 追加写入文件
    local file = io.open(stats_file, "a")
    if file then
        file:write(record .. "\n")
        file:close()
    end
end

-- 辅助函数：计算百分比
local function calculate_percentage(count, total)
    if total == 0 then return 0 end
    return math.floor((count / total) * 1000 + 0.5) / 10  -- 保留1位小数
end

-- 辅助函数：格式化数字，右对齐
local function format_number(num, width)
    local str = tostring(num)
    return string.rep(" ", width - #str) .. str
end

-- 辅助函数：格式化文本，左对齐
local function format_text(text, width)
    text = text or ""
    if #text > width then
        text = text:sub(1, width - 3) .. "..."
    end
    return text .. string.rep(" ", width - #text)
end

-- 辅助函数：格式化百分比
local function format_percentage(percentage, width)
    local str = tostring(percentage) .. "%"
    return string.rep(" ", width - #str) .. str
end

-- 完成 - 读取文件进行表格统计
function done(summary, latency, requests)
    print("\n" .. string.rep("=", 60))
    print("                   响应统计表")
    print(string.rep("=", 60))
    
    -- 读取文件统计
    local stats = {}
    local total_responses = 0
    
    local file = io.open(stats_file, "r")
    if file then
        for line in file:lines() do
            local code, content = line:match("^(%d+)|(.+)$")
            if code and content then
                total_responses = total_responses + 1
                
                -- 创建唯一键：状态码+内容
                local key = code .. ":" .. content
                if not stats[key] then
                    stats[key] = {
                        code = code,
                        content = content,
                        count = 0
                    }
                end
                stats[key].count = stats[key].count + 1
            end
        end
        file:close()
        
        -- 删除临时文件
        os.remove(stats_file)
    end
    
    -- 按状态码和数量排序
    local sorted_stats = {}
    for _, data in pairs(stats) do
        table.insert(sorted_stats, data)
    end
    
    table.sort(sorted_stats, function(a, b)
        if a.code == b.code then
            return a.count > b.count
        end
        return a.code < b.code
    end)
    
    -- 打印表头
    print("状态码   响应内容" .. string.rep(" ", 25) .. "数量    占比")
    print(string.rep("-", 60))
    
    local current_code = nil
    
    for i, data in ipairs(sorted_stats) do
        local percentage = calculate_percentage(data.count, total_responses)
        
        -- 如果是新的状态码，打印状态码行
        if data.code ~= current_code then
            if current_code then
                print("")  -- 状态码之间的空行
            end
            current_code = data.code
            print(data.code .. "：")
        end
        
        -- 打印响应内容行
        local content_display = data.content
        if content_display == "" then
            content_display = "(空内容)"
        end
        
        print("          " .. format_text(content_display, 30) .. 
              format_number(data.count, 8) .. "   " .. 
              format_percentage(percentage, 6))
    end
    
    -- 打印总计
    print(string.rep("-", 60))
    print("总计" .. string.rep(" ", 37) .. 
          format_number(total_responses, 8) .. "   " .. 
          format_percentage(100, 6))
    
    -- WRK统计信息
    print("\n" .. string.rep("=", 60))
    print("WRK基础统计")
    print(string.rep("-", 60))
    print("总请求数: " .. (summary.requests or 0))
    
    local error_count = (summary.errors.status or 0) + 
                       (summary.errors.read or 0) + 
                       (summary.errors.write or 0) + 
                       (summary.errors.timeout or 0)
    print("错误数: " .. error_count)
    print("文件记录响应数: " .. total_responses)
    
    -- 如果有记录缺失，显示警告
    local wrk_requests = summary.requests or 0
    if total_responses > 0 and total_responses ~= wrk_requests - error_count then
        local missing = wrk_requests - error_count - total_responses
        if missing > 0 then
            print("警告: 有 " .. missing .. " 个响应未被记录")
        end
    end
    
    print(string.rep("=", 60))
end