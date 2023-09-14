package io.appservice.module;

interface IAppService{
    boolean show(in String id,
              in String url,
              in int closeSize,
              in String closePosition,
              in int [] margins,
              in String intent,
              in String pkg,
              in String cls,
              in long timeout);
    void hide();
}