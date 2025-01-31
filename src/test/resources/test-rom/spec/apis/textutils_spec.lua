describe("The textutils library", function()
    describe("textutils.slowWrite", function()
        it("validates arguments", function()
            expect.error(textutils.slowWrite, nil, false):eq("bad argument #2 (expected number, got boolean)")
        end)
    end)

    describe("textutils.formatTime", function()
        it("validates arguments", function()
            textutils.formatTime(0)
            textutils.formatTime(0, false)
            expect.error(textutils.formatTime, nil):eq("bad argument #1 (expected number, got nil)")
            expect.error(textutils.formatTime, 1, 1):eq("bad argument #2 (expected boolean, got number)")
        end)
    end)

    describe("textutils.pagedPrint", function()
        it("validates arguments", function()
            expect.error(textutils.pagedPrint, nil, false):eq("bad argument #2 (expected number, got boolean)")
        end)
    end)

    describe("textutils.tabulate", function()
        it("validates arguments", function()
            term.redirect(window.create(term.current(), 1, 1, 5, 5, false))

            textutils.tabulate()
            textutils.tabulate({ "test" })
            textutils.tabulate(colors.white)

            expect.error(textutils.tabulate, nil):eq("bad argument #1 (expected number or table, got nil)")
            expect.error(textutils.tabulate, { "test" }, nil):eq("bad argument #2 (expected number or table, got nil)")
        end)
    end)

    describe("textutils.pagedTabulate", function()
        it("validates arguments", function()
            term.redirect(window.create(term.current(), 1, 1, 5, 5, false))

            textutils.pagedTabulate()
            textutils.pagedTabulate({ "test" })
            textutils.pagedTabulate(colors.white)

            expect.error(textutils.pagedTabulate, nil):eq("bad argument #1 (expected number or table, got nil)")
            expect.error(textutils.pagedTabulate, { "test" }, nil):eq("bad argument #2 (expected number or table, got nil)")
        end)
    end)

    describe("textutils.empty_json_array", function()
        it("is immutable", function()
            expect.error(function() textutils.empty_json_array[1] = true end)
                :eq("textutils_spec.lua:51: attempt to mutate textutils.empty_json_array")
        end)
    end)

    describe("textutils.unserialise", function()
        it("validates arguments", function()
            textutils.unserialise("")
            expect.error(textutils.unserialise, nil):eq("bad argument #1 (expected string, got nil)")
        end)
    end)

    describe("textutils.serialiseJSON", function()
        it("validates arguments", function()
            textutils.serialiseJSON("")
            textutils.serialiseJSON(1)
            textutils.serialiseJSON({})
            textutils.serialiseJSON(false)
            textutils.serialiseJSON("", true)
            expect.error(textutils.serialiseJSON, nil):eq("bad argument #1 (expected table, string, number or boolean, got nil)")
            expect.error(textutils.serialiseJSON, "", 1):eq("bad argument #2 (expected boolean, got number)")
        end)

        it("serializes empty arrays", function()
            expect(textutils.serializeJSON(textutils.empty_json_array)):eq("[]")
        end)

        it("serializes null", function()
            expect(textutils.serializeJSON(textutils.json_null)):eq("null")
        end)

        it("serializes strings", function()
            expect(textutils.serializeJSON('a')):eq('"a"')
            expect(textutils.serializeJSON('"')):eq('"\\""')
            expect(textutils.serializeJSON('\\')):eq('"\\\\"')
            expect(textutils.serializeJSON('/')):eq('"/"')
            expect(textutils.serializeJSON('\b')):eq('"\\b"')
            expect(textutils.serializeJSON('\n')):eq('"\\n"')
            expect(textutils.serializeJSON(string.char(0))):eq('"\\u0000"')
            expect(textutils.serializeJSON(string.char(0x0A))):eq('"\\n"')
            expect(textutils.serializeJSON(string.char(0x1D))):eq('"\\u001D"')
            expect(textutils.serializeJSON(string.char(0x81))):eq('"\\u0081"')
            expect(textutils.serializeJSON(string.char(0xFF))):eq('"\\u00FF"')
        end)

        it("serializes arrays until the last index with content", function()
            expect(textutils.serializeJSON({ 5, "test", nil, nil, 7 })):eq('[5,"test",null,null,7]')
            expect(textutils.serializeJSON({ 5, "test", nil, nil, textutils.json_null })):eq('[5,"test",null,null,null]')
            expect(textutils.serializeJSON({ nil, nil, nil, nil, "text" })):eq('[null,null,null,null,"text"]')
        end)
    end)

    describe("textutils.unserializeJSON", function()
        describe("parses", function()
            it("a list of primitives", function()
                expect(textutils.unserializeJSON('[1, true, false, "hello"]')):same { 1, true, false, "hello" }
            end)

            it("null when parse_null is true", function()
                expect(textutils.unserializeJSON("null", { parse_null = true })):eq(textutils.json_null)
            end)

            it("null when parse_null is false", function()
                expect(textutils.unserializeJSON("null", { parse_null = false })):eq(nil)
            end)

            it("an empty array", function()
                expect(textutils.unserializeJSON("[]", { parse_null = false })):eq(textutils.empty_json_array)
            end)

            it("basic objects", function()
                expect(textutils.unserializeJSON([[{ "a": 1, "b":2 }]])):same { a = 1, b = 2 }
            end)
        end)

        describe("parses using NBT-style syntax", function()
            local function exp(x)
                local res, err = textutils.unserializeJSON(x, { nbt_style = true })
                if not res then error(err, 2) end
                return expect(res)
            end
            it("basic objects", function()
                exp([[{ a: 1, b:2 }]]):same { a = 1, b = 2 }
            end)

            it("suffixed numbers", function()
                exp("1b"):eq(1)
                exp("1.1d"):eq(1.1)
            end)

            it("strings", function()
                exp("'123'"):eq("123")
                exp("\"123\""):eq("123")
            end)

            it("typed arrays", function()
                exp("[B; 1, 2, 3]"):same { 1, 2, 3 }
                exp("[B;]"):same {}
            end)
        end)

        describe("passes nst/JSONTestSuite", function()
            local search_path = "test-rom/data/json-parsing"
            local skip = dofile(search_path .. "/skip.lua")
            for _, file in pairs(fs.find(search_path .. "/*.json")) do
                local name = fs.getName(file):sub(1, -6);
                (skip[name] and pending or it)(name, function()
                    local h = io.open(file, "r")
                    local contents = h:read("*a")
                    h:close()

                    local res, err = textutils.unserializeJSON(contents)
                    local kind = fs.getName(file):sub(1, 1)
                    if kind == "n" then
                        expect(res):eq(nil)
                    elseif kind == "y" then
                        if err ~= nil then fail("Expected test to pass, but failed with " .. err) end
                    end
                end)
            end
        end)
    end)

    describe("textutils.urlEncode", function()
        it("validates arguments", function()
            textutils.urlEncode("")
            expect.error(textutils.urlEncode, nil):eq("bad argument #1 (expected string, got nil)")
        end)
    end)

    describe("textutils.complete", function()
        it("validates arguments", function()
            textutils.complete("pri")
            textutils.complete("pri", _G)
            expect.error(textutils.complete, nil):eq("bad argument #1 (expected string, got nil)")
            expect.error(textutils.complete, "", false):eq("bad argument #2 (expected table, got boolean)")
        end)
    end)
end)
