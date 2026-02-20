
## Backend
Run the backend in one terminal, in watch mode:
```shell
deder exec -m backend -t run -w
```

When you change the backend code, Deder will automatically recompile it and restart the backend server.

## Frontend
Run Vite in another terminal:
```shell
npm run dev
```


When you change the frontend code, Deder will recompile it + link it, and then Vite will reload the page.
