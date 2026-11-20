const { defineConfig } = require('@playwright/test');
module.exports = defineConfig({
    testDir: './tests',
    testMatch: '*.spec.cjs',
    timeout: 120000,
    expect: { timeout: 15000 },
    workers: 1,
    retries: 0,
    reporter: [['list'], ['html', { open: 'never' }], ['junit', { outputFile: 'test-results/e2e.xml' }]],
    use: {
        trace: 'retain-on-failure',
        launchOptions: {
            env: {
                ...process.env,
                ELECTRON_EXTRA_LAUNCH_ARGS: process.env.CI ? '--no-sandbox --autoplay-policy=no-user-gesture-required' : '--autoplay-policy=no-user-gesture-required',
            },
        },
    },
});
