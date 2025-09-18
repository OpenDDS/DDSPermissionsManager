import globals from 'globals';
import pluginJs from '@eslint/js';
import pluginSvelte from 'eslint-plugin-svelte';
import parserSvelte from 'svelte-eslint-parser';
import tsEslint from '@typescript-eslint/eslint-plugin';
import tsParser from '@typescript-eslint/parser';

export default [
	{
		ignores: ['.svelte-kit/', 'build/']
	},
	pluginJs.configs.recommended,
	// Configuration for Svelte files
	...pluginSvelte.configs['flat/recommended'],
	{
		files: ['**/*.svelte'],
		languageOptions: {
			globals: {
				...globals.browser,
				...globals.node
			},
			parser: parserSvelte,
			parserOptions: {
				ecmaVersion: 2020,
				sourceType: 'module',
				parser: tsParser
			}
		},
		plugins: {
			'@typescript-eslint': tsEslint
		},
		rules: {
			// Add your custom Svelte rules here
		}
	},
	// Configuration for JavaScript/TypeScript files
	{
		files: ['**/*.{js,mjs,cjs,ts}'],
		languageOptions: {
			globals: {
				...globals.browser,
				...globals.node
			},
			parser: tsParser, // Use TypeScript parser for JS/TS files
			parserOptions: {
				ecmaVersion: 2020,
				sourceType: 'module'
			}
		},
		plugins: {
			'@typescript-eslint': tsEslint
		},
		rules: {
			// Add your custom JS/TS rules here
		}
	}
];
