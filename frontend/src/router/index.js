import { createRouter, createWebHistory } from 'vue-router';

const routes = [
  {
    path: '/',
    name: 'Home',
    component: () => import('../views/Home.vue')
  },
  {
    path: '/stations',
    name: 'Stations',
    component: () => import('../views/Stations.vue')
  }
];

const router = createRouter({
  history: createWebHistory(),
  routes
});

export default router;
