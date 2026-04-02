const express = require("express");
const fetch = require("node-fetch");
const cors = require("cors");

const app = express();
app.use(cors());

// Static frontend serve karega
app.use(express.static("public"));

// 🔥 Proxy route
app.get("/proxy", async (req, res) => {
  const url = req.query.url;

  if (!url) return res.send("No URL");

  try {
    const response = await fetch(url, {
      headers: {
        "Referer": "http://stvlive.net/"
      }
    });

    res.set("Access-Control-Allow-Origin", "*");
    response.body.pipe(res);

  } catch (err) {
    res.status(500).send("Error fetching stream");
  }
});

const PORT = process.env.PORT || 10000;
app.listen(PORT, () => console.log("Server running on " + PORT));
