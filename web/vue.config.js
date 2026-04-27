const AutoImport = require('unplugin-auto-import/webpack').default
const Components = require('unplugin-vue-components/webpack').default
const { ElementPlusResolver } = require('unplugin-vue-components/resolvers')

module.exports = {
	devServer: {
		port: 8082,
		proxy: {
			'/api': {
				target: 'http://127.0.0.1:8888',
				changeOrigin: true,
				ws: false,
				pathRewrite: {
					'^/api': ''
				}
			},
			'/rtc': {
				target: 'http://127.0.0.1:8890',
				changeOrigin: true,
				ws: false,
				pathRewrite: {
					'^/rtc': ''
				}
			},
			'/web3': {
				target: 'http://127.0.0.1:8901',
				changeOrigin: true,
				ws: false,
				pathRewrite: {
					'^/web3': ''
				}
			}
		}
	},
	css: {
		extract: {
			ignoreOrder: true
		},
		loaderOptions: {
			sass: {
				sassOptions: {
					quietDeps: true
				}
			}
		}
	},
	productionSourceMap: false,
	configureWebpack: {
		plugins: [
			AutoImport({
				dts: false,
				resolvers: [ElementPlusResolver()]
			}),
			Components({
				dts: false,
				resolvers: [ElementPlusResolver({ directives: true })]
			})
		],
		optimization: {
			splitChunks: {
				chunks: 'all',
				cacheGroups: {
					element: {
						name: 'chunk-element',
						test: /[\\\\/]node_modules[\\\\/]element-plus[\\\\/]/,
						priority: 20
					},
					vendors: {
						name: 'chunk-vendors',
						test: /[\\\\/]node_modules[\\\\/]/,
						priority: 10,
						reuseExistingChunk: true
					},
					common: {
						name: 'chunk-common',
						minChunks: 2,
						priority: 5,
						reuseExistingChunk: true
					}
				}
			}
		}
	}

}
