module.exports = {
    // default working directory (can be changed per 'cwd' in every asset option)
    context: __dirname,

    // path to the clientlib root folder (output)
    clientLibRoot: "./../ui.apps/src/main/content/jcr_root/apps/wknd/clientlibs",

    libs: [
        {
            name: "clientlib-site",
            allowProxy: true,
            categories: ["wknd.site"],
            dependencies: ['aem-core-cif-react-components'],
            serializationFormat: "xml",
            cssProcessor : ["default:none", "min:none"],
            jsProcessor: ["default:none", "min:none"],
            assets: {
                js: [
                    "dist/clientlib-site/vendors~site.js",
                    "dist/clientlib-site/site.js"
                ],
                css: [
                    "dist/clientlib-site/css/vendors~site.*.css",
                    "dist/clientlib-site/css/site.*.css"
                ],
                resources: {
                    cwd: "./dist/clientlib-site/resources",
                    flatten: false,
                    files: ["**/*.*"]
                }
            }
        }
    ]
};