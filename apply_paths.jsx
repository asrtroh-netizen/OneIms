// Apply paths.json into the active After Effects composition as TRACE_LINES.
// Expects: active comp + selected footage/image layer matching reference.png.

(function applyTracePaths() {
    function readFileText(path) {
        var f = new File(path);
        if (!f.exists) {
            throw new Error("paths.json not found: " + path);
        }
        f.encoding = "UTF-8";
        f.open("r");
        var t = f.read();
        f.close();
        return t;
    }

    function parsePathsJson(text) {
        // Prefer native JSON if available (AE 2022+)
        if (typeof JSON !== "undefined" && JSON.parse) {
            return JSON.parse(text);
        }
        throw new Error("JSON.parse unavailable in this AE version");
    }

    function scriptFolder() {
        return File($.fileName).parent;
    }

    function getActiveComp() {
        var comp = app.project.activeItem;
        if (!(comp && comp instanceof CompItem)) {
            throw new Error("No active composition. Please open a comp first.");
        }
        return comp;
    }

    function getReferenceLayer(comp) {
        var sel = comp.selectedLayers;
        if (!sel || sel.length < 1) {
            throw new Error("Please select the reference.png image layer in the active comp.");
        }
        var layer = sel[0];
        if (!(layer instanceof AVLayer)) {
            throw new Error("Selected layer is not an AV/image layer.");
        }
        return layer;
    }

    function deleteExistingTrace(comp) {
        for (var i = comp.numLayers; i >= 1; i--) {
            var L = comp.layer(i);
            if (L && L.name === "TRACE_LINES") {
                L.remove();
            }
        }
    }

    function setLayerIdentityTopLeft(layer) {
        var tg = layer.property("ADBE Transform Group");
        tg.property("ADBE Anchor Point").setValue([0, 0]);
        tg.property("ADBE Position").setValue([0, 0]);
        tg.property("ADBE Scale").setValue([100, 100]);
        try {
            tg.property("ADBE Rotate Z").setValue(0);
        } catch (e) {
            tg.property("ADBE Rotate").setValue(0);
        }
        try {
            tg.property("ADBE Opacity").setValue(100);
        } catch (e2) {}
    }

    function imagePointToComp(refLayer, x, y) {
        // sourcePointToComp maps footage pixel coords -> composition coords.
        return refLayer.sourcePointToComp([x, y]);
    }

    function makeShapeFromPath(pathObj, refLayer) {
        var verts = [];
        var inT = [];
        var outT = [];
        for (var i = 0; i < pathObj.vertices.length; i++) {
            var v = pathObj.vertices[i];
            var cp = imagePointToComp(refLayer, v[0], v[1]);
            verts.push([cp[0], cp[1]]);

            // Tangents are relative offsets in image space; convert by sampling nearby point.
            var it = pathObj.inTangents[i];
            var ot = pathObj.outTangents[i];
            var inAbs = imagePointToComp(refLayer, v[0] + it[0], v[1] + it[1]);
            var outAbs = imagePointToComp(refLayer, v[0] + ot[0], v[1] + ot[1]);
            inT.push([inAbs[0] - cp[0], inAbs[1] - cp[1]]);
            outT.push([outAbs[0] - cp[0], outAbs[1] - cp[1]]);
        }
        var sh = new Shape();
        sh.vertices = verts;
        sh.inTangents = inT;
        sh.outTangents = outT;
        sh.closed = !!pathObj.closed;
        return sh;
    }

    function addLineGroup(contents, pathObj, refLayer) {
        var group = contents.addProperty("ADBE Vector Group");
        group.name = pathObj.name;

        var groupContents = group.property("ADBE Vectors Group");
        var pathProp = groupContents.addProperty("ADBE Vector Shape - Group");
        pathProp.name = "Path 1";
        pathProp.property("ADBE Vector Shape").setValue(makeShapeFromPath(pathObj, refLayer));

        var stroke = groupContents.addProperty("ADBE Vector Graphic - Stroke");
        stroke.property("ADBE Vector Stroke Color").setValue([1, 1, 1, 1]);
        stroke.property("ADBE Vector Stroke Width").setValue(2);
        stroke.property("ADBE Vector Stroke Opacity").setValue(100);
        // Round cap / round join
        try {
            stroke.property("ADBE Vector Stroke Line Cap").setValue(2); // Round Cap
            stroke.property("ADBE Vector Stroke Line Join").setValue(2); // Round Join
        } catch (e) {}

        // Explicitly no fill in this group
        // (do not add fill property)
        return group;
    }

    app.beginUndoGroup("Apply TRACE_LINES from paths.json");
    try {
        var folder = scriptFolder();
        var jsonPath = folder.fsName + "/paths.json";
        var data = parsePathsJson(readFileText(jsonPath));
        if (!data.paths || !data.paths.length) {
            throw new Error("paths.json contains no paths");
        }

        var comp = getActiveComp();
        var refLayer = getReferenceLayer(comp);

        deleteExistingTrace(comp);

        var shapeLayer = comp.layers.addShape();
        shapeLayer.name = "TRACE_LINES";
        // Keep above reference for visibility
        shapeLayer.moveToBeginning();
        setLayerIdentityTopLeft(shapeLayer);

        var contents = shapeLayer.property("ADBE Root Vectors Group");
        for (var i = 0; i < data.paths.length; i++) {
            addLineGroup(contents, data.paths[i], refLayer);
        }

        // Deselect others, select new layer for inspection
        for (var li = 1; li <= comp.numLayers; li++) {
            comp.layer(li).selected = false;
        }
        shapeLayer.selected = true;

        var okMsg =
            "TRACE_LINES created with " +
            data.paths.length +
            " editable open paths.\nComp: " +
            comp.name +
            "\nRef layer: " +
            refLayer.name;
        var logFile = new File(folder.fsName + "/ae_apply_result.txt");
        logFile.encoding = "UTF-8";
        logFile.open("w");
        logFile.write("OK\n" + okMsg + "\n");
        logFile.close();
        // Non-blocking status for automation; still visible in AE Info if needed
        try {
            writeLn(okMsg.replace(/\n/g, " | "));
        } catch (e3) {}
    } catch (err) {
        try {
            var errFile = new File(File($.fileName).parent.fsName + "/ae_apply_result.txt");
            errFile.encoding = "UTF-8";
            errFile.open("w");
            errFile.write("FAIL\n" + String(err) + "\n");
            errFile.close();
        } catch (e4) {}
        alert("apply_paths.jsx failed:\n" + String(err));
        throw err;
    } finally {
        app.endUndoGroup();
    }
})();
