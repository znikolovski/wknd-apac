const merge = require('webpack-merge');
const common = require('./webpack.common.js');
const path = require('path');
const HtmlWebpackPlugin = require('html-webpack-plugin');

const SOURCE_ROOT = __dirname + '/src/main/webpack';

module.exports = merge(common, {
   mode: 'development',
   devtool: 'inline-source-map',
   performance: {hints: "warning"},
   plugins: [
      new HtmlWebpackPlugin({
         template: path.resolve(__dirname, SOURCE_ROOT + '/static/index.html')
      })
   ],
   resolve: {
      // during development we may have dependencies which are linked in node_modules using either `npm link`
      // or `npm install <file dir>`. Those dependencies will bring *all* their dependencies along, because
      // in that case npm ignores the "devDependencies" setting.
      // In that case, we need to make sure that this project using its own version of React libraries.

      alias: {
          react: path.resolve('./node_modules/react'),
          'react-dom': path.resolve('./node_modules/react-dom'),
          'react-i18next': path.resolve('./node_modules/react-i18next'),
          '@apollo/client': path.resolve('./node_modules/@apollo/client')
      }
   },
   devServer: {
      inline: true,
      proxy: [{
         context: ['/content', '/etc.clientlibs'],
         target: 'http://localhost:4502',
      }]
   }
});