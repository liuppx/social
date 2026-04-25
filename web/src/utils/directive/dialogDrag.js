const dialogDragDirective = {
    mounted(el) {
        const dialogHeaderEl = el.querySelector('.el-dialog__header')
        const dragDom = el.querySelector('.el-dialog')
        if (!dialogHeaderEl || !dragDom) {
            return
        }
        dialogHeaderEl.style.cursor = 'move'

        const sty = dragDom.currentStyle || window.getComputedStyle(dragDom, null)

        dialogHeaderEl.onmousedown = (e) => {
            const disX = e.clientX - dialogHeaderEl.offsetLeft
            const disY = e.clientY - dialogHeaderEl.offsetTop
            const screenWidth = document.body.clientWidth
            const screenHeight = document.documentElement.clientHeight
            const dragDomWidth = dragDom.offsetWidth
            const dragDomheight = dragDom.offsetHeight
            const minDragDomLeft = dragDom.offsetLeft
            const maxDragDomLeft = screenWidth - dragDom.offsetLeft - dragDomWidth
            const minDragDomTop = dragDom.offsetTop
            const maxDragDomTop = screenHeight - dragDom.offsetTop - dragDomheight

            let styL
            let styT
            if (sty.left.includes('%')) {
                styL = +document.body.clientWidth * (+sty.left.replace(/\%/g, '') / 100)
                styT = +document.body.clientHeight * (+sty.top.replace(/\%/g, '') / 100)
            } else {
                styL = +sty.left.replace(/\px/g, '')
                styT = +sty.top.replace(/\px/g, '')
            }

            document.onmousemove = function (evt) {
                let l = evt.clientX - disX
                let t = evt.clientY - disY

                if (-l > minDragDomLeft) {
                    l = -minDragDomLeft
                } else if (l > maxDragDomLeft) {
                    l = maxDragDomLeft
                }
                if (-t > minDragDomTop) {
                    t = -minDragDomTop
                } else if (t > maxDragDomTop) {
                    t = maxDragDomTop
                }
                dragDom.style.left = `${l + styL}px`
                dragDom.style.top = `${t + styT}px`
            }

            document.onmouseup = function () {
                document.onmousemove = null
                document.onmouseup = null
            }
        }
    }
}

export default function registerDialogDrag(app) {
    app.directive('dialogDrag', dialogDragDirective)
}
