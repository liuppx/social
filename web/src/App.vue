<template>
  <div id="app">
    <router-view></router-view>
  </div>
</template>

<script>
export default {
  name: 'App',
  components: {}
}
</script>

<style lang="scss">
#app {
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  position: absolute;
  height: 100%;
  width: 100%;
  color: var(--yeying-text-color);
  font-family: var(--yeying-font-family);
}
</style>
